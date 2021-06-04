package com.github.AndrewAlbizati;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.user.UserStatus;

import java.util.Scanner;

public class Bot {
    public static DiscordApi api;
    public static void main(String[] args) {
        // Get the bot token from the user
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter bot token: ");
        String token = sc.nextLine();

        // Make sure token is valid
        if (token == null) {
            return;
        }

        if (token.length() == 0) {
            return;
        }

        // Create the bot
        api = new DiscordApiBuilder().setToken(token).setAllIntents().login().join();

        // Let the user know the bot is working correctly
        System.out.println("Logged in as " + api.getYourself().getDiscriminatedName());


        // Set bot status
        api.updateStatus(UserStatus.ONLINE);
        api.updateActivity(ActivityType.PLAYING, "Type !scat to start a game.");


        // Add message create listener for the !scat command
        api.addMessageCreateListener(event -> {
            if (event.getMessageContent().equalsIgnoreCase("!scat") || event.getMessage().getContent().equalsIgnoreCase("!scattergories")) {
                Scattergories scattergories = new Scattergories(event, api);
                scattergories.start();
            }
        });
    }
}
