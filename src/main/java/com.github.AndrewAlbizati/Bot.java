package com.github.AndrewAlbizati;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.user.UserStatus;
import org.javacord.api.interaction.SlashCommand;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;

public class Bot {
    public static DiscordApi api;
    public static void main(String[] args) {
        // Get the bot token from config.properties
        String token = "";
        try {
            File f = new File("config.properties");
            if (f.createNewFile()) {
                System.out.println(f.getName() + " created.");
                FileWriter fw = new FileWriter("config.properties");
                fw.write("token=");
                fw.close();
            }

            Properties prop = new Properties();
            FileInputStream ip = new FileInputStream("config.properties");
            prop.load(ip);
            ip.close();
            token = prop.getProperty("token");

            if (token == null) {
                throw new NullPointerException();
            }

            if (token.length() == 0) {
                throw new NullPointerException("Please add the bot's token to config.properties");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println();
            System.exit(1);
        }

        // Create the bot
        api = new DiscordApiBuilder().setToken(token).login().join();

        // Let the user know the bot is working correctly
        System.out.println("Logged in as " + api.getYourself().getDiscriminatedName());


        // Set bot status
        api.updateStatus(UserStatus.ONLINE);
        api.updateActivity(ActivityType.PLAYING, "Type /scattergories to start a game");

        // Create slash command (may take a few mins to update on Discord)
        SlashCommand.with("scattergories", "Plays a game of Scattergories that other players can join").createGlobal(api).join();

        // Add message create listener for the !scattergories or !scat command
        api.addSlashCommandCreateListener(event -> {
            if (event.getSlashCommandInteraction().getCommandName().equalsIgnoreCase("scattergories")) {
                // Start Scattergories in a new thread to avoid issues with running long code in event listeners
                Scattergories scattergories = new Scattergories(event.getSlashCommandInteraction(), api);
                scattergories.start();
            }
        });
    }
}
