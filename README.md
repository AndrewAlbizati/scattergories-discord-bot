# Scattergories Discord Bot
This bot is used to play the game Scattergories in Discord. This bot and all its code was written in Java using IntelliJ IDEA. This bot was made using the Javacord library.

## Setup
First, enter ```java -jar ScattergoriesBot.jar``` into a command prompt. Upon startup, you will have to enter a valid Discord bot token into the console. Setup is complete after that.

## What is Scattergories
In Scattergories, each player is given the same letter and twelve different categories, for example, "A boy's name". Each player then has to come up with a word or phrase that starts with the letter given. After the answering period has ended, players answered will be compared against each other. Any players that get the same answers do not earn any points. The player with the most points after the 12 rounds wins.

## How to Play
Please use "!scat" or "!scattergories" to start a game. A message will appear and players will need to react to it with a 👍 to join the game. 

## Customizing the game
The player who started the game will be messaged instructions on how to customize the game. They can change the letter, time, start the game early, or stop the game.

## Adding Answers
To add answers, each player will need to message the bot something like this: "1. Answer". All answers must start with the same letter, and that letter is chosen at the start of the game.

## Comparing Answers
After the answering round has ended (by default 240 seconds), each player will need to return to the channel which the original command was issued. The bot will send the category, letter, and the answers that players submitted. The players react to each individual answer with ✅ to approve it or ❌ to reject it. In the event of a tie, the answer is rejected. Each of these rounds can last at most 60 seconds. 

## Dependencies
- Javacord 3.3.2 (https://github.com/Javacord/Javacord)
- JSON-Simple 1.1.1 (https://github.com/fangyidong/json-simple)