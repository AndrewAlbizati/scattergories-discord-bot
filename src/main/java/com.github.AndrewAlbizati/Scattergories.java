package com.github.AndrewAlbizati;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.Reaction;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Scattergories extends Thread {
    private HashMap<User, Message> messages = new HashMap<>(); // HashMap to store messages that were sent to each player in the game
    private static Random rand = new Random();
    private HashMap<User, ArrayList<String>> answers = new HashMap<>(); // Hashmap of User and String ArrayList pairs. Tracks answers that the users have given
    private int categoryCount = 12;
    private String letterIMGLink; // Link to an image to the letter chosen. This can change if the game creator wants to change it
    private ArrayList<User> players = new ArrayList(); // List of all players who reacted with thumbs up to original message
    private int maxPlayers = 6;

    private AtomicBoolean running = new AtomicBoolean(true); // AtomicBoolean to know if the game has ended or not. The game ends if the creator types !stop
    private AtomicBoolean waitingForPlayers = new AtomicBoolean(true); // AtomicBoolean to know if the game has started or not. Game starts when creator types !start

    private static HashMap<String, String> images = new HashMap<>(); // HashMap of all images that the bot can use

    private static final Color embedColor = new Color(155, 89, 182);

    private final MessageCreateEvent event;
    private final DiscordApi api;
    public Scattergories(MessageCreateEvent event, DiscordApi api) {
        this.event = event;
        this.api = api;
        try {
            // Store the links to all images into the "images" HashMap
            URL url = ClassLoader.getSystemClassLoader().getResource("imagelinks.json");
            InputStream jsonStream = new FileInputStream(url.getFile());

            JSONParser parser = new JSONParser();
            JSONObject imageLinks = (JSONObject) parser.parse(new InputStreamReader(jsonStream, "UTF-8"));

            for (Object key : imageLinks.keySet()) {
                images.put(key.toString(), imageLinks.get(key).toString());
            }

        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        TextChannel channel = event.getChannel();
        Message message = event.getMessage();

        // Checks if the message was sent in a DM
        if (channel.asPrivateChannel().isPresent()) {
            return;
        }

        User creator = message.getAuthor().asUser().get();

        // Set up first embed sent to all users
        // Lets them know how to join the game and how the game works
        EmbedBuilder firstEmbed = new EmbedBuilder();
        firstEmbed.setTitle("Scattergories");
        firstEmbed.setDescription("In Scattergories, each player will be given the same letter and twelve different categories, like \"A boy's name\", and each player has to come up with a word that starts with the letter given." +
                "\n\nWhen the clock reaches 0, each answer will be compared, and if two or more players have the same answer, neither player gets any points.");

        firstEmbed.addField("How to Join", "React with 👍 to join the game.");
        firstEmbed.setThumbnail(images.get("scat-logo").toString());
        firstEmbed.setColor(embedColor);


        Message firstMessage = channel.sendMessage(firstEmbed).join();
        firstMessage.addReaction("👍").join();


        AtomicReference<String> letter = new AtomicReference<>(newLetter("abcdefghijklmnoprstw")); // excludes q u v x y z
        AtomicInteger gameTime = new AtomicInteger(240); // How long the game will last


        // Message sent to creator to let them customize the game
        EmbedBuilder embedToCreator = new EmbedBuilder();
        embedToCreator.setTitle("Scattergories Settings");
        embedToCreator.addField("Change Letter", "Type \"!newletter\" to change the letter.");
        embedToCreator.addField("Change Category Count", "You can change the amount of categories by typing \"!categories <number>\". The game will have **" + categoryCount + "** categories.");
        embedToCreator.addField("Change Time Length", "Type \"!time <seconds>\" to change the length of the game. The current length is **" + gameTime.get() + "** seconds.");
        embedToCreator.addField("Starting a Game", "You can start a game by typing \"!start\".");
        embedToCreator.addField("Stopping a Game", "You can stop a game by typing \"!stop\".");
        embedToCreator.setThumbnail(images.get(letter.get().toLowerCase()));
        embedToCreator.setColor(embedColor);

        Message setupMessage = creator.sendMessage(embedToCreator).join();

        int secondsUntilStart = 30;


        AtomicInteger atomCategoryCount = new AtomicInteger(categoryCount);
        // Admin settings message create listener
        api.addMessageCreateListener(messageCreateEvent -> {
            if (!waitingForPlayers.get())
                return;

            Message messageReceived = messageCreateEvent.getMessage();

            // Message must be sent in a DM
            if (!messageReceived.getChannel().asPrivateChannel().isPresent())
                return;

            // User who sent the message is the creator
            if (!creator.equals(messageCreateEvent.getMessageAuthor().asUser().get()))
                return;

            // Starts the game by setting waitingForPlayers to false
            if (messageReceived.getContent().equalsIgnoreCase("!start"))
                waitingForPlayers.set(false);

            // Ignores messages that arent commands
            if (!messageReceived.getContent().equalsIgnoreCase("!newletter") && !messageReceived.getContent().toLowerCase().startsWith("!time") && !messageReceived.getContent().toLowerCase().startsWith("!categories"))
                return;

            // Checks if command was "!time"
            if (messageReceived.getContent().toLowerCase().startsWith("!time") && isInt(messageReceived.getContent().split(" ")[1])) {
                gameTime.set(Integer.parseInt(messageReceived.getContent().split(" ")[1]));

            // Checks if command was "!newletter"
            } else if (messageReceived.getContent().toLowerCase().startsWith("!newletter")) {
                String currentLetter = letter.get().toLowerCase();
                letter.set(newLetter("abcdefghijklmnoprstw".replaceAll(currentLetter, "")));

            } else if (messageReceived.getContent().toLowerCase().startsWith("!categories")) {
                int num = Integer.parseInt(messageReceived.getContent().split(" ")[1]);
                atomCategoryCount.set(num);
            }
            // Resend message to game creator
            embedToCreator.removeAllFields();
            embedToCreator.addField("Change Letter", "Type \"!newletter\" to change the letter. The current letter is **" + letter.get().toUpperCase() + "**.");
            embedToCreator.addField("Change Category Count", "You can change the amount of categories by typing \"!categories <number>\". The game will have **" + atomCategoryCount.get() + "** categories.");
            embedToCreator.addField("Change Time Length", "Type \"!time <seconds>\" to change the length of the game. The current length is **" + gameTime.get() + "** seconds.");
            embedToCreator.addField("Starting a Game", "You can start a game by typing \"!start\".");
            embedToCreator.addField("Stopping a Game", "You can stop a game by typing \"!stop\".");
            embedToCreator.setThumbnail(images.get(letter.get().toLowerCase()).toString());
            setupMessage.edit(embedToCreator).join();
        }).removeAfter(secondsUntilStart, TimeUnit.SECONDS);

        // Counts down until start, once finished it gets all people who reacted to the original message
        players = countdownToStart(firstMessage, setupMessage, secondsUntilStart);

        categoryCount = atomCategoryCount.get();

        // Generate letters, categories
        ArrayList<String> categories = new ArrayList();
        JSONArray allCategories;
        try {
            FileReader reader = new FileReader("categories.json");
            JSONParser parser = new JSONParser();
            allCategories = (JSONArray)parser.parse(reader);
        } catch (IOException | ParseException e) {
            e.printStackTrace();
            channel.sendMessage("Categories.json was not found. The game has been cancelled.");
            return;
        }

        // Select all the categories from categories.json
        for (int i = 0; i < categoryCount; i++) {
            int index = rand.nextInt(allCategories.size());

            Object category = allCategories.get(index);
            categories.add((String) category);
            allCategories.remove(category);
        }


        // Stop if only one player joined
        if (players.size() <= 1) {
            firstMessage.delete().join();
            return;
        }

        // Stop if more than the max amount of players joined
        if (players.size() > maxPlayers) {
            firstMessage.delete().join();
            return;
        }

        // Setting up answers Hashmap
        for (int i = 0; i < players.size(); i++) {
            User u = players.get(i);
            ArrayList<String> blanks = new ArrayList<>();
            for (int j = 0; j < categoryCount; j++) {
                blanks.add("");
            }
            answers.put(u, blanks);
        }

        EmbedBuilder categoryEmbed = new EmbedBuilder();


        StringBuilder categoryMessageBuilder = new StringBuilder();

        // Create the categories message
        for (int i = 0; i < categories.size(); i++) {
            categoryMessageBuilder.append((i + 1) + ". " + categories.get(i) + "\n");
        }

        categoryEmbed.setTitle("Scattergories");
        categoryEmbed.addField("Categories", categoryMessageBuilder.toString());
        categoryEmbed.addField("How to Answer", "To answer, type <number>. <answer>. For example, \"7. Answer\".");

        letterIMGLink = images.get(letter.get().toLowerCase()).toString();
        categoryEmbed.setThumbnail(letterIMGLink);

        categoryEmbed.setColor(embedColor);

        // Set up the messages hashmap
        for (int i = 0; i < players.size(); i++) {
            messages.put(players.get(i), players.get(i).sendMessage(categoryEmbed).join());
        }

        // Event handler for adding answers for each category
        // User provided a number
        // Make sure number is 1-12

        ListenerManager<MessageCreateListener> messageHandler = api.addMessageCreateListener(messageCreateEvent -> {
            if (!running.get())
                return;

            if (messageCreateEvent.getMessageAuthor().asUser().get().getIdAsString().equals(creator.getIdAsString()) && messageCreateEvent.getMessage().getContent().equalsIgnoreCase("!stop"))
                running.set(false);

            try {
                Message userSentMsg = messageCreateEvent.getMessage();

                if (!players.contains(userSentMsg.getAuthor().asUser().get()) || !userSentMsg.getChannel().asPrivateChannel().isPresent())
                    return;

                // User provided a number
                if (!isInt(userSentMsg.getReadableContent().split("[.]")[0]))
                    return;

                User user = messageCreateEvent.getChannel().asPrivateChannel().get().getRecipient().get();
                String[] userSubmittedAnswers = userSentMsg.getReadableContent().split("\n");
                for (String s : userSubmittedAnswers) {
                    // User provided a number
                    if (!isInt(s.split("[.]")[0]))
                        continue;

                    int categoryNumber = Integer.parseInt(s.split("[.]")[0]);
                    String categoryAnswer = s.replaceFirst(s.split("[.]")[0], "").substring(1);

                    // Make sure answer is between 0-100 characters
                    if (categoryAnswer.length() == 0 || categoryAnswer.length() > 100) {
                        user.sendMessage("Answer must be between 1 and 100 characters long.");
                        continue;
                    }

                    // Make sure number is 1-12
                    if (categoryNumber < 0 || categoryNumber > categoryCount + 1)
                        continue;

                    // Remove spaces before the answer
                    while (categoryAnswer.startsWith(" "))
                        categoryAnswer = categoryAnswer.substring(1);

                    if (!categoryAnswer.toLowerCase().startsWith(letter.get())) {
                        user.sendMessage("That doesn't start with the letter **" + letter + "**!");
                        continue;
                    }

                    ArrayList<String> lowercaseAnswers = new ArrayList<>();
                    for (String answer : answers.get(user)) {
                        lowercaseAnswers.add(answer.toLowerCase());
                    }

                    if (lowercaseAnswers.contains(categoryAnswer.toLowerCase())) {
                        user.sendMessage("You've already used this answer!");
                        continue;
                    }

                    answers.get(user).set(categoryNumber - 1, categoryAnswer);
                }

                Message botSentMessage = messages.get(messageCreateEvent.getChannel().asPrivateChannel().get().getRecipient().get());


                EmbedBuilder sendingEmbed = new EmbedBuilder();
                sendingEmbed.setTitle("Scattergories");
                sendingEmbed.setColor(embedColor);
                sendingEmbed.setThumbnail(letterIMGLink);
                sendingEmbed.setFooter(botSentMessage.getEmbeds().get(0).getFooter().get().getText().get());

                String userCategories = botSentMessage.getEmbeds().get(0).getFields().get(0).getValue();

                StringBuilder newCategoryMessageBuilder = new StringBuilder();
                // Update categories message with new answer
                int userAnswerCount = 0;
                for (int i = 0; i < userCategories.split("\n").length; i++) {
                    if (i + 1 == categories.size()) {
                        newCategoryMessageBuilder.append((i + 1) + ". " + categories.get(i) + " **" + answers.get(user).get(i) + "**\n");
                        userAnswerCount++;
                        continue;
                    }

                    String category = userCategories.split("\n")[i] + "\n";
                    newCategoryMessageBuilder.append(category);
                    if (category.contains("**")) {
                        userAnswerCount++;
                    }
                }

                sendingEmbed.addField("Categories (" + userAnswerCount + " / " + categoryCount + ")", newCategoryMessageBuilder.toString());

                sendingEmbed.addField(botSentMessage.getEmbeds().get(0).getFields().get(1).getName(), botSentMessage.getEmbeds().get(0).getFields().get(1).getValue());

                Message latestMessage = user.sendMessage(sendingEmbed).join();
                botSentMessage.delete();

                messages.put(messageCreateEvent.getChannel().asPrivateChannel().get().getRecipient().get(), latestMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).removeAfter(gameTime.get() + 100, TimeUnit.SECONDS);


        countdownToEnd(gameTime.get());
        messageHandler.remove();

        if (!running.get()) {
            return;
        }
        // Checks if there are answer users with no answers
        // Removes player if they didn't answer (AFK player)

        ArrayList<Object> AFKUsers = new ArrayList();
        for (Object key : answers.keySet()) {
            ArrayList<String> a = answers.get(key);

            StringBuilder emptyChecker = new StringBuilder();
            for (int i = 0; i < a.size(); i++) {
                emptyChecker.append(a.get(i));
            }
            if (emptyChecker.toString().length() == 0) {
                AFKUsers.add(key);
            }
        }
        for (int i = 0; i < AFKUsers.size(); i++) {
            answers.remove(AFKUsers.get(i));
            players.remove(AFKUsers.get(i));
        }




        // Iterate through all categories
        // Store them in answersByRound ArrayList of User,String HashMaps
        ArrayList<HashMap<User, String>> answersByRound = new ArrayList();
        for (int i = 0; i < categoryCount; i++) {
            HashMap<User, String> currentAnswers = new HashMap<>();
            for (Object key : answers.keySet()) {
                if (answers.get(key).get(i).length() > 0) {
                    currentAnswers.put((User) key, answers.get(key).get(i));
                }
            }
            answersByRound.add(currentAnswers);
        }

        // If there's an empty answer, it is set to null
        for (int i = 0; i < answersByRound.size(); i++) {
            StringBuilder testForAnswers = new StringBuilder();
            for (Object key : answersByRound.get(i).keySet()) {
                testForAnswers.append(answersByRound.get(i).get(key));
            }
            if (testForAnswers.toString().length() == 0) {
                answersByRound.set(i, null);
            }
        }


        // Ends the game if there is one or less players
        if (players.size() == 0) {
            return;
        }

        if (players.size() == 1) {
            int points = 0;
            for (int i = 0; i < answersByRound.size(); i++) {
                if (answersByRound.get(i) != null) {
                    points++;
                }
            }

            EmbedBuilder em = new EmbedBuilder();
            em.setTitle("Scattergories");
            em.setDescription("You are the only non-AFK player left, so you win by default!\n\n" +
                    "You had " + points + " points!");
            em.setThumbnail(images.get("winner").toString());
            em.setColor(embedColor);
            em.setFooter("Thanks for playing!");
            players.get(0).sendMessage(em);
            return;
        }


        // Lets the user know that the answering period has ended
        EmbedBuilder answersDoneBuilder = new EmbedBuilder()
                .setColor(embedColor)
                .setTitle("Scattergories")
                .setDescription("Time's up! Please return to <#" + channel.getIdAsString() + "> to compare answers.")
                .setFooter("Thanks for submitting your answers!")
                .setThumbnail(images.get("scat-logo"));
        // Send to all players
        for (int i = 0; i < players.size(); i++) {
            players.get(i).sendMessage(answersDoneBuilder);
        }

        // Set up points HashMap
        HashMap<User, Integer> points = new HashMap();
        for (int i = 0; i < players.size(); i++) {
            points.put(players.get(i), 0);
        }

        // Go through all answers by round
        for (int i = 0; i < answersByRound.size(); i++) {
            EmbedBuilder roundEmbed = new EmbedBuilder();
            roundEmbed.setColor(embedColor);
            roundEmbed.setTitle("Scattergories");
            roundEmbed.setDescription("Round **" + (i + 1) + "**\n" + categories.get(i));
            roundEmbed.setThumbnail(letterIMGLink);

            HashMap<User, String> round = answersByRound.get(i);
            HashMap<Message, User> msgs = new HashMap<>();

            // Round with no answers
            if (round == null) {
                roundEmbed.setDescription("Round **" + (i + 1) + "**\n" + categories.get(i) + "\n\nThere were no answers!");
                msgs.put(channel.sendMessage(roundEmbed).join(), null);
                countDownRounds(msgs, 10, players);
                continue;

            }

            // Round with at least one answer
            HashMap<Message, User> messageToSubmitter = new HashMap();


            msgs.put(channel.sendMessage(roundEmbed).join(), null);
            // Send all user-submitted answers
            for (Object key : answersByRound.get(i).keySet()) {
                User submitter = (User) key;
                Message m = channel.sendMessage(submitter.getName() + ": " + answersByRound.get(i).get(key)).join();
                messageToSubmitter.put(m, submitter);
                m.addReaction("✅");
                m.addReaction("❌");
                msgs.put(m, (User) key);
            }


            HashMap<User, HashMap<String, Integer>> roundPoints = countDownRounds(msgs, 60, players);


            StringBuilder resultsBuilder = new StringBuilder();

            // Go through each answer, determine if it is accepted or rejected
            for (Object key : roundPoints.keySet()) {
                HashMap<String, Integer> currentPoints = roundPoints.get(key);

                User u = (User) key;
                resultsBuilder.append(u.getName());
                int yesVoteCount = currentPoints.get("yes");
                int noVoteCount = currentPoints.get("no");
                resultsBuilder.append(" (" + yesVoteCount + " yes / " + noVoteCount + " no)");
                // More yes than no
                if (yesVoteCount > noVoteCount) {
                    points.put(u, points.get(u) + 1);
                    resultsBuilder.append(" ✅");
                    // More no than yes, or same amount
                } else if (yesVoteCount <= noVoteCount) {
                    // User didn't get any points
                    resultsBuilder.append(" ❌");
                }
                resultsBuilder.append("\n");
            }


            // Show results for each answer
            for (Object key : msgs.keySet()) {
                Message m = (Message) key;
                if (!m.getEmbeds().isEmpty()) {
                    EmbedBuilder em = m.getEmbeds().get(0).toBuilder();
                    em.addField("Results", resultsBuilder.toString());
                    m.edit(em);
                }
            }
        }

        // Set up rankings message
        EmbedBuilder rankingsBuilder = new EmbedBuilder()
                .setTitle("Scattergories")
                .setFooter("Thanks for playing!");

        LinkedHashMap<User, Integer> rankingsMap = new LinkedHashMap<>();
        for (Object key : points.keySet()) {
            User u = (User) key;
            rankingsMap.put(u, Integer.parseInt(points.get(key).toString()));
        }

        // Sort the LinkedHashMap
        List<Map.Entry<User, Integer>> entries = new ArrayList<Map.Entry<User, Integer>>( rankingsMap.entrySet() );
        Collections.sort(entries, Comparator.comparingInt(Map.Entry::getValue));


        rankingsMap.clear();
        for(Map.Entry<User, Integer> entry : entries) {
            rankingsMap.put(entry.getKey(), entry.getValue());
        }


        List<User> reverseOrderedKeys = new ArrayList<User>(rankingsMap.keySet());
        Collections.reverse(reverseOrderedKeys);



        StringBuilder rankingsString = new StringBuilder();
        int j = 0;
        for (int i = 0; i < reverseOrderedKeys.size(); i++) {
            // Puts score for the first user
            if (i == 0) {
                rankingsString.append((j + 1) + ". " + reverseOrderedKeys.get(i).getName() + " (" + points.get(reverseOrderedKeys.get(i)) + " point" + (points.get(reverseOrderedKeys.get(i)) != 1 ? "s" : "") + ")");

                // Current person has same amount of points as previous person (tie)
            } else if (points.get(reverseOrderedKeys.get(i - 1)) == points.get(reverseOrderedKeys.get(i))) {
                rankingsString.append(", " + reverseOrderedKeys.get(i).getName() + " (" + points.get(reverseOrderedKeys.get(i)) + " point" + (points.get(reverseOrderedKeys.get(i)) != 1 ? "s" : "") + ")");
                continue;
                // Puts score for users
            } else {
                rankingsString.append("\n" + (j + 1) + ". " + reverseOrderedKeys.get(i).getName() + " (" + points.get(reverseOrderedKeys.get(i)) + " point" + (points.get(reverseOrderedKeys.get(i)) != 1 ? "s" : "") + ")");
            }
            j++;
        }

        rankingsBuilder.addField("Rankings", rankingsString.toString());
        rankingsBuilder.setColor(embedColor);
        rankingsBuilder.setThumbnail(images.get("scat-logo").toString());

        channel.sendMessage(rankingsBuilder);
    }

    private static boolean isInt(String s) {
        try {
            int i = Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String newLetter(String letters) {
        return String.valueOf(letters.charAt(rand.nextInt(letters.length())));
    }

    private ArrayList<User> countdownToStart(Message m, Message adminMessage, int secondsUntilStart) {
        ArrayList<User> players = new ArrayList();

        while (secondsUntilStart > 0) {
            players = new ArrayList();
            secondsUntilStart--;
            secondsUntilStart--;


            try {
                // Get users who reacted to initial message with 👍
                for (int i = 0; i < m.getReactions().size(); i++) {
                    Reaction reaction = m.getReactions().get(i);
                    List<User> users = reaction.getUsers().get();
                    if (!reaction.getEmoji().equalsEmoji("👍")) {
                        continue;
                    }

                    for (int j = 0; j < users.size(); j++) {
                        if (!users.get(j).isBot()) {
                            players.add(users.get(j));
                        }
                    }
                }

                EmbedBuilder builder = m.getEmbeds().get(0).toBuilder();
                builder.removeAllFields();
                builder.addField("How to Join", "React with 👍 to join the game.");



                // Update message so it displays users who joined
                if (players.size() > 0) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < players.size(); i++) {
                        b.append((i + 1) + ". " + players.get(i).getName() + "\n");
                        // If there are too many players
                        if (i > maxPlayers) {
                            break;
                        }
                    }
                    builder.addField("Players", b.toString());
                }

                // Update the timer
                EmbedBuilder adminEmbedBuilder = adminMessage.getEmbeds().get(0).toBuilder();
                if (secondsUntilStart == 1) {
                    m.edit(builder.setFooter("The game will start in " + secondsUntilStart + " second."));
                    adminMessage.edit(adminEmbedBuilder.setFooter("The game will start in " + secondsUntilStart + " second."));
                } else {
                    m.edit(builder.setFooter("The game will start in " + secondsUntilStart + " seconds."));
                    adminMessage.edit(adminEmbedBuilder.setFooter("The game will start in " + secondsUntilStart + " seconds."));
                }

                if (!waitingForPlayers.get()) {
                    break;
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
                m.getChannel().sendMessage("Error: " + e.getMessage());
            }

        }
        // Show that the game has started once the timer has ended
        EmbedBuilder builder = m.getEmbeds().get(0).toBuilder();
        builder.removeAllFields();
        builder.addField("How to Join", "React with 👍 to join the game.");

        if (players.size() > 0) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                b.append((i + 1) + ". " + players.get(i).getName() + "\n");
            }
            builder.addField("Players", b.toString());
        }

        m.edit(builder.setFooter("The game has started."));

        adminMessage.delete().join();

        return players;
    }

    private void countdownToEnd(int secondsUntilEnd) {
        try {
            while (secondsUntilEnd > 0) {
                if (!running.get()) {
                    break;
                }
                // Update timer for each user
                for (Object keyObj : messages.keySet()) {
                    EmbedBuilder sendingEmbed = messages.get(keyObj).getEmbeds().get(0).toBuilder();
                    if (secondsUntilEnd == 1) {
                        sendingEmbed.setFooter("The game will end in " + secondsUntilEnd + " second.");
                    } else {
                        sendingEmbed.setFooter("The game will end in " + secondsUntilEnd + " seconds.");
                    }
                    try {
                        messages.get(keyObj).edit(sendingEmbed).join();
                    } catch (Exception e) {
                        // Random error, somehow doesn't break the entire game
                    }

                }
                secondsUntilEnd--;
                secondsUntilEnd--;
                Thread.sleep(2000);
            }
            for (Object keyObj : messages.keySet()) {
                EmbedBuilder sendingEmbed = messages.get(keyObj).getEmbeds().get(0).toBuilder();
                sendingEmbed.setFooter("The game has ended.");

                messages.get(keyObj).edit(sendingEmbed).join();
            }


        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private HashMap<User, HashMap<String, Integer>> countDownRounds(HashMap<Message, User> messagesMap, int seconds, ArrayList<User> users) {
        try {
            ArrayList<Message> messages = new ArrayList();
            for (Object o : messagesMap.keySet()) {
                messages.add((Message) o);
            }

            ArrayList<Message> embedMessages = new ArrayList();
            ArrayList<Message> reactMessages = new ArrayList();

            // Get messages with embeds and messages without embeds
            for (int i = 0; i < messages.size(); i++) {
                if (!messages.get(i).getEmbeds().isEmpty()) {
                    embedMessages.add(messages.get(i));
                } else {
                    reactMessages.add(messages.get(i));
                }
            }

            boolean stop = false;
            while (seconds > 0) {
                seconds--;
                seconds--;
                for (int i = 0; i < embedMessages.size(); i++) {
                    Message m = embedMessages.get(i);
                    EmbedBuilder builder = m.getEmbeds().get(0).toBuilder();
                    // Update timer
                    if (seconds == 1) {
                        builder.setFooter("The round will end in " + seconds + " second.");
                    } else {
                        builder.setFooter("The round will end in " + seconds + " seconds.");
                    }

                    m.edit(builder);

                }

                // Go through answers, check if all players have voted
                for (int i = 0; i < reactMessages.size(); i++) {
                    try {
                        Message m = reactMessages.get(i);
                        ArrayList<User> ups = new ArrayList();
                        ArrayList<User> downs = new ArrayList();
                        try {
                            if (!m.getReactionByEmoji("✅").isPresent() || !m.getReactionByEmoji("❌").isPresent()) {
                                break;
                            }

                            List <User> upsTemp = m.getReactionByEmoji("✅").get().getUsers().get();
                            List <User> downsTemp = m.getReactionByEmoji("❌").get().getUsers().get();
                            // Remove bots
                            for (int j = 0; j < upsTemp.size(); j++) {
                                for (User u : users) {
                                    if (u.getIdAsString().equalsIgnoreCase(upsTemp.get(j).getIdAsString())) {
                                        ups.add(upsTemp.get(j));
                                    }
                                }

                            }
                            for (int j = 0; j < downsTemp.size(); j++) {
                                boolean flag = true;
                                for (User u1 : ups) {
                                    if (u1.getIdAsString().equalsIgnoreCase(downsTemp.get(j).getIdAsString())) {
                                        flag = false;
                                    }
                                }
                                if (!flag) {
                                    continue;
                                }

                                for (User u : users) {
                                    if (u.getIdAsString().equalsIgnoreCase(downsTemp.get(j).getIdAsString())) {
                                        downs.add(downsTemp.get(j));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }

                        // End game if all users have voted
                        if (ups.size() + downs.size() >= players.size()) {
                            stop = true;
                        } else {
                            stop = false;
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (stop) {
                    break;
                }
                Thread.sleep(2000);
            }
            // End voting round
            for (int i = 0; i < embedMessages.size(); i++) {
                Message m = embedMessages.get(i);
                EmbedBuilder builder = m.getEmbeds().get(0).toBuilder();
                builder.setFooter("The round has ended.");
                m.edit(builder);
            }
            HashMap<User, HashMap<String, Integer>> pointsMap = new HashMap();

            // Remove bot submitted emojis
            for (int i = 0; i < reactMessages.size(); i++) {
                Message m = reactMessages.get(i);
                m.removeOwnReactionByEmoji("✅");
                m.removeOwnReactionByEmoji("❌");

                ArrayList<User> ups = new ArrayList();
                ArrayList<User> downs = new ArrayList();


                // Get all users that voted yes
                try {
                    List <User> upsTemp = m.getReactionByEmoji("✅").get().getUsers().get();
                    for (int j = 0; j < upsTemp.size(); j++) {
                        if (!upsTemp.get(j).isBot()) {
                            ups.add(upsTemp.get(j));
                        }
                    }
                } catch (Exception e) {
                    // There were no reactions
                }

                // Get all users that voted no
                try {
                    List <User> downsTemp = m.getReactionByEmoji("❌").get().getUsers().get();
                    for (int j = 0; j < downsTemp.size(); j++) {
                        if (!downsTemp.get(j).isBot()) {
                            if (!ups.contains(downsTemp.get(j))) {
                                downs.add(downsTemp.get(j));
                            }
                        }
                    }
                } catch (Exception e) {
                    // There were no reactions
                }


                // Set up HashMap to return each answer with its votes
                HashMap<String, Integer> temp = new HashMap();
                temp.put("yes", ups.size());
                temp.put("no", downs.size());
                pointsMap.put(messagesMap.get(m), temp);
            }

            return pointsMap;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
