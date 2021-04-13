package com.github.AndrewAlbizati;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.user.UserStatus;

import java.util.Scanner;

public class Bot {
    public static DiscordApi api;
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        System.out.print("Enter bot token: ");
        String token = sc.nextLine();
        if (token == null) {
            return;
        }

        if (token.length() == 0) {
            return;
        }
        api = new DiscordApiBuilder().setToken(token).setAllIntents().login().join();

        System.out.println("Logged in as " + api.getYourself().getDiscriminatedName());

        api.updateStatus(UserStatus.ONLINE);

        api.updateActivity(ActivityType.PLAYING, "Type !scat to start a game.");

        api.addMessageCreateListener(new Scattergories());


    }
}
