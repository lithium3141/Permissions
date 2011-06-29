package com.nijiko.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.bukkit.Server;
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

	@Test
	public void testMessageHelper() {
		MessageHelper target = new MessageHelper(null);
		Assert.assertNull("MessageHelper did not accept null CommandSender", target.sender);
		
		MockCommandSender mockSender = new MockCommandSender();
		target = new MessageHelper(mockSender);
		Assert.assertEquals("MessageHelper did not store mock CommandSender", mockSender, target.sender);
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
		fail("Not yet implemented");
	}

	@Test
	public void testSendString() {
		fail("Not yet implemented");
	}

}
