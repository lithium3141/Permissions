package com.nijiko.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Assert;

import org.bukkit.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nijiko.MessageHelper;

public class MessageHelperTest {
	
	@SuppressWarnings("serial")
	private List<String> recognizedColors = new ArrayList<String>() {{
		add("black");
		add("navy");
		add("green");
		add("teal");
		add("red");
		add("purple");
		add("gold");
		add("silver");
		add("gray");
		add("blue");
		add("lime");
		add("aqua");
		add("rose");
		add("pink");
		add("yellow");
		add("white");
	}};
	
	@SuppressWarnings("serial")
	private Map<String, String> testStrings = new HashMap<String, String>() {{
		put("There are no replacements in this string.", "There are no replacements in this string.");
		put("This string is <blue>half blue.", "This string is \u00A79half blue.");
		put("This string is &4half red.", "This string is \u00A74half red.");
		put("This string <teal>is a &5rainbow!", "This string \u00A73is a \u00A75rainbow!");
	}};
	
	protected class MockCommandSender implements org.bukkit.command.CommandSender {
		
		// List of messages received, in chronological order
		protected List<String> sentMessages;
		
		MockCommandSender() {
			this.sentMessages = new ArrayList<String>();
		}

		@Override
		public Server getServer() {
			return null;
		}

		@Override
		public boolean isOp() {
			return false;
		}

		@Override
		public void sendMessage(String message) {
			this.sentMessages.add(message);
		}
		
	}
	
	protected MessageHelper target;
	protected MockCommandSender mockSender;
	
	@Before
	public void setUp() {
		this.mockSender = new MockCommandSender();
		this.target = new MessageHelper(this.mockSender);
	}
	
	@After
	public void tearDown() {
		this.target = null;
		this.mockSender = null;
	}

	@Test
	public void testMessageHelperNull() {
		MessageHelper target = new MessageHelper(null);
		Assert.assertNull("MessageHelper did not accept null CommandSender", target.sender);
	}
	
	@Test
	public void testMessageHelperCommandSender() {
		Assert.assertEquals("MessageHelper did not store mock CommandSender", this.mockSender, this.target.sender);
	}

	@Test
	public void testParseNoReplace() {
		Assert.assertEquals("This string has no colors!", MessageHelper.parse("This string has no colors!"));
		Assert.assertEquals("This one doesn't either!", MessageHelper.parse("This one doesn't either!"));
		Assert.assertEquals("Here are some color names: blue red yellow green", MessageHelper.parse("Here are some color names: blue red yellow green"));
		Assert.assertEquals("And some special characters: &x &z &? &*", MessageHelper.parse("And some special characters: &x &z &? &*"));
	}
	
	@Test
	public void testParseSingleReplace() {
		for(int i = 0; i < this.recognizedColors.size(); i++) {
			Assert.assertEquals("\u00A7" + Integer.toHexString(i), MessageHelper.parse("&" + Integer.toHexString(i)));
		}
	}
	
	@Test
	public void testParseInlineReplace() {
		for(int i = 0; i < this.recognizedColors.size(); i++) {
			String startString = "This is a color: &" + Integer.toHexString(i) + ". Isn't it nice?";
			Assert.assertEquals("This is a color: \u00A7" + Integer.toHexString(i) + ". Isn't it nice?", MessageHelper.parse(startString));
		}
	}
	
	@Test
	public void testParseMultipleReplace() {
		for(int i = 1; i < this.recognizedColors.size(); i++) {
			String startString = "Multiple colors! &" + Integer.toHexString(i - 1) + " &" + Integer.toHexString(i);
			Assert.assertEquals("Multiple colors! \u00A7" + Integer.toHexString(i - 1) + " \u00A7" + Integer.toHexString(i), MessageHelper.parse(startString));
		}
	}
	
	@Test
	public void testColorizeNoReplace() {
		Assert.assertEquals("This string has no colors!", MessageHelper.colorize("This string has no colors!"));
		Assert.assertEquals("This one doesn't either!", MessageHelper.colorize("This one doesn't either!"));
		Assert.assertEquals("Here are some color names: blue red yellow green", MessageHelper.colorize("Here are some color names: blue red yellow green"));
		Assert.assertEquals("And some special characters: <> <fuchsia> <mauve> <taupe>", MessageHelper.colorize("And some special characters: <> <fuchsia> <mauve> <taupe>"));
	}

	@Test
	public void testColorizeSingleReplace() {
		for(int i = 0; i < this.recognizedColors.size(); i++) {
			Assert.assertEquals("\u00A7" + Integer.toHexString(i), MessageHelper.colorize("<" + this.recognizedColors.get(i) + ">"));
		}
	}
	
	@Test
	public void testColorizeInlineReplace() {
		for(int i = 0; i < this.recognizedColors.size(); i++) {
			String startString = "This is a color: <" + this.recognizedColors.get(i) + ">. Isn't it nice?";
			Assert.assertEquals("This is a color: \u00A7" + Integer.toHexString(i) + ". Isn't it nice?", MessageHelper.colorize(startString));
		}
	}
	
	@Test
	public void testColorizeMultipleReplace() {
		for(int i = 1; i < this.recognizedColors.size(); i++) {
			String startString = "Multiple colors! <" + this.recognizedColors.get(i - 1) + "> <" + this.recognizedColors.get(i) + ">";
			Assert.assertEquals("Multiple colors! \u00A7" + Integer.toHexString(i - 1) + " \u00A7" + Integer.toHexString(i), MessageHelper.colorize(startString));
		}
	}

	@Test
	public void testSendCommandSenderString() {
		// Even though there's one available, create a new sender to ensure the static
		// method is doing what it's supposed to, not some crazy singleton reflection magic.
		MockCommandSender sender = new MockCommandSender();
		
		int messagesSent = 0;
		for(Entry<String, String> testEntry : this.testStrings.entrySet()) {
			String message = testEntry.getKey();
			String expected = testEntry.getValue();
			
			MessageHelper.send(sender, message);
			messagesSent += 1;
			
			Assert.assertEquals("MessageHelper lost track of message", messagesSent, sender.sentMessages.size());
			Assert.assertEquals("MessageHelper did not parse string properly", expected, sender.sentMessages.get(sender.sentMessages.size() - 1));
		}
	}

	@Test
	public void testSendStringBasic() {
		int messagesSent = 0;
		for(Entry<String, String> testEntry : this.testStrings.entrySet()) {
			String message = testEntry.getKey();
			String expected = testEntry.getValue();
			
			this.target.send(message);
			messagesSent += 1;
			
			Assert.assertEquals("MessageHelper lost track of message", messagesSent, this.mockSender.sentMessages.size());
			Assert.assertEquals("MessageHelper did not parse string properly", expected, this.mockSender.sentMessages.get(this.mockSender.sentMessages.size() - 1));
		}
	}

}
