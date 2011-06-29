package com.nijiko;

import org.bukkit.command.CommandSender;

/**
 * Permissions 2.x
 * Copyright (C) 2011  Matt 'The Yeti' Burnett <admin@theyeticave.net>
 * Original Credit & Copyright (C) 2010 Nijikokun <nijikokun@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Permissions Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Permissions Public License for more details.
 *
 * You should have received a copy of the GNU Permissions Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * MessagingHelper.java <br />
 * Renamed from Messaging.java to avoid conflicts with iConomy, due to switching static methods to instance methods.<br />
 * 
 * Lets us do fancy pantsy things with colors, messages, and broadcasting :D!
 * 
 * @author Nijikokun <nijikokun@gmail.com>
 */
public class MessageHelper {

    public final CommandSender sender;

    public MessageHelper(CommandSender sender) {
        this.sender = sender;
    }
//    /**
//     * Converts a list of arguments into points.
//     * 
//     * @param original
//     *            The original string necessary to convert inside of.
//     * @param arguments
//     *            The list of arguments, multiple arguments are seperated by
//     *            commas for a single point.
//     * @param points
//     *            The point used to alter the argument.
//     * 
//     * @return <code>String</code> - The parsed string after converting
//     *         arguments to variables (points)
//     */
//    public static String argument(String original, String[] arguments,
//            String[] points) {
//        for (int i = 0; i < arguments.length; i++) {
//            if (arguments[i].contains(",")) {
//                for (String arg : arguments[i].split(",")) {
//                    original = original.replace(arg, points[i]);
//                }
//            } else {
//                original = original.replace(arguments[i], points[i]);
//            }
//        }
//
//        return original;
//    }

    /**
     * Parses the original string against color specific codes. This one
     * converts &[code] to \u00A7[code] <br />
     * <br />
     * Example: <blockquote>
     * 
     * <pre>
     * Messaging.parse(&quot;Hello &amp;2world!&quot;); // returns: Hello \u00A72world!
     * </pre>
     * 
     * </blockquote>
     * 
     * @param original
     *            The original string used for conversions.
     * 
     * @return <code>String</code> - The parsed string after conversion.
     */
    public static String parse(String original) {
        original = colorize(original);
        return original.replaceAll("(&([a-f0-9]))", "\u00A7$2").replace("&&",
                "&");
    }

    /**
     * Converts color codes into the simoleon code. Sort of a HTML format color
     * code tag.
     * <p>
     * Color codes allowed: black, navy, green, teal, red, purple, gold, silver,
     * gray, blue, lime, aqua, rose, pink, yellow, white.
     * </p>
     * Example: <blockquote
     * 
     * <pre>
     * Messaging.colorize(&quot;Hello &lt;green&gt;world!&quot;); // returns: Hello \u00A72world!
     * </pre>
     * 
     * </blockquote>
     * 
     * @param original
     *            Original string to be parsed against group of color names.
     * 
     * @return <code>String</code> - The parsed string after conversion.
     */
    public static String colorize(String original) {
        // Removed the weird character
        return original.replace("<black>", "\u00A70")
                .replace("<navy>", "\u00A71").replace("<green>", "\u00A72")
                .replace("<teal>", "\u00A73").replace("<red>", "\u00A74")
                .replace("<purple>", "\u00A75").replace("<gold>", "\u00A76")
                .replace("<silver>", "\u00A77").replace("<gray>", "\u00A78")
                .replace("<blue>", "\u00A79").replace("<lime>", "\u00A7a")
                .replace("<aqua>", "\u00A7b").replace("<rose>", "\u00A7c")
                .replace("<pink>", "\u00A7d").replace("<yellow>", "\u00A7e")
                .replace("<white>", "\u00A7f");
    }

    /**
     * Sends a message to a specific CommandSender. <br />
     * <br />
     * Example: <blockquote>
     * 
     * <pre>
     * Messaging.send(sender, &quot;This will go to the CommandSender specified.&quot;);
     * </pre>
     * 
     * </blockquote>
     * 
     * @param sender
     *            CommandSender we are sending the message to.
     * @param message
     *            The message to be sent.
     */
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(parse(message));
    }

    /**
     * Sends a message to the stored CommandSender.
     * 
     * @param message
     *            The message to be sent.
     * @see MessageHelper#save(CommandSender)
     */
    public void send(String message) {
        if (this.sender != null)
        	this.sender.sendMessage(parse(message));
    }
}
