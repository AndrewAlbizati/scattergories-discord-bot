package com.github.AndrewAlbizati;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.user.UserStatus;

import java.io.File;
import java.util.Scanner;

public class Bot {
    public static DiscordApi api;
    public static void main(String[] args) {
        // Get the bot token from the user
        String token = "";
        try {
            File f = new File("token.txt");
            if (f.createNewFile())
                System.out.println(f.getName() + " created.");

            Scanner s = new Scanner(f);
            token = s.next();
            s.close();

            if (token.length() == 0)
                throw new NullPointerException();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Please add the bot's token to token.txt");
            System.exit(1);
        }

        // Create the bot
        api = new DiscordApiBuilder().setToken(token).setAllIntents().login().join();

        // Let the user know the bot is working correctly
        System.out.println("Logged in as " + api.getYourself().getDiscriminatedName());


        // Set bot status
        api.updateStatus(UserStatus.ONLINE);
        api.updateActivity(ActivityType.PLAYING, "Type !scat to start a game.");


        // Add message create listener for the !scattergories or !scat command
        api.addMessageCreateListener(event -> {
            if (event.getMessageContent().equalsIgnoreCase("!scat") || event.getMessage().getContent().equalsIgnoreCase("!scattergories")) {
                // Start Scattergories in a new thread to avoid issues with running long code in MessageCreateListeners
                Scattergories scattergories = new Scattergories(event, api);
                scattergories.start();
            }
        });
    }
}
