/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.odinms.client.messages.commands.donator;

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.Buffs.MapleStat;
import net.sf.odinms.client.messages.DonatorCommand;
import net.sf.odinms.client.messages.DonatorCommandDefinition;
import net.sf.odinms.client.messages.MessageCallback;

/**
 *
 * @author Admin
 */
public class ApManipulationCommands implements DonatorCommand {

    public void reduceAP(MapleClient c, byte stat, short amount) {
        MapleCharacter player = c.getPlayer();
        switch (stat) {
            case 1: // STR
                player.getStat().setStr(player.getStat().getStr() - amount);
                player.updateSingleStat(MapleStat.STR, player.getStat().getStr());
                break;
            case 2: // DEX
                player.getStat().setDex(player.getStat().getDex() - amount);
                player.updateSingleStat(MapleStat.DEX, player.getStat().getDex());
                break;
            case 3: // INT
                player.getStat().setInt(player.getStat().getInt() - amount);
                player.updateSingleStat(MapleStat.INT, player.getStat().getInt());
                break;
            case 4: // LUK
                player.getStat().setLuk(player.getStat().getLuk() - amount);
                player.updateSingleStat(MapleStat.LUK, player.getStat().getLuk());
                break;
            case 5:
                player.setStorageAp(player.getStorageAp() - amount);
                break;
        }
        player.setRemainingAp(player.getRemainingAp() + amount);
        player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
    }

    public void claimAp(MapleCharacter player, short amount) {
        if (amount < 0) {
            player.ban("trying to restore negative Ap");
            return;
        }
        if ((player.getRemainingAp() + amount) <= Short.MAX_VALUE) {
            player.reduceStorageAp(amount);
            player.setRemainingAp(player.getRemainingAp() + amount);
            player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
        } else {
            player.dropMessage("you cannot have more than 32767 Ap in your remaining Ap");
        }
    }

    public void resetAP(MapleClient c, byte stat) {
        MapleCharacter player = c.getPlayer();
        short amount = 0;
        switch (stat) {
            case 1: // STR
                amount = (short) (player.getStat().getStr() - 4);
                if ((player.getRemainingAp() + amount) > 32767) {
                    c.showMessage("Trying to get negative AP? you cannot have more than 32767 free AP. now go fap");
                    return;
                }
                player.getStat().setStr(4);
                player.updateSingleStat(MapleStat.STR, 4);
                break;
            case 2: // DEX
                amount = (short) (player.getStat().getDex() - 4);
                if ((player.getRemainingAp() + amount) > 32767) {
                    c.showMessage("Trying to get negative AP? you cannot have more than 32767 free AP. now go fap");
                    return;
                }
                player.getStat().setDex(4);
                player.updateSingleStat(MapleStat.DEX, 4);
                break;
            case 3: // INT
                amount = (short) (player.getStat().getInt() - 4);
                if ((player.getRemainingAp() + amount) > 32767) {
                    c.showMessage("Trying to get negative AP? you cannot have more than 32767 free AP. now go fap");
                    return;
                }
                player.getStat().setInt(4);
                player.updateSingleStat(MapleStat.INT, 4);
                break;
            case 4: // LUK
                amount = (short) (player.getStat().getLuk() - 4);
                if ((player.getRemainingAp() + amount) > 32767) {
                    c.showMessage("Trying to get negative AP? you cannot have more than 32767 free AP. now go fap");
                    return;
                }
                player.getStat().setLuk(4);
                player.updateSingleStat(MapleStat.LUK, 4);
                break;
        }
        player.setRemainingAp(player.getRemainingAp() + amount);
        player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
    }

    public void addAP(MapleClient c, byte stat, short amount) {
        MapleCharacter player = c.getPlayer();
        switch (stat) {
            case 1: // STR
                player.getStat().setStr(player.getStat().getStr() + amount);
                player.updateSingleStat(MapleStat.STR, player.getStat().getStr());
                break;
            case 2: // DEX
                player.getStat().setDex(player.getStat().getDex() + amount);
                player.updateSingleStat(MapleStat.DEX, player.getStat().getDex());
                break;
            case 3: // INT
                player.getStat().setInt(player.getStat().getInt() + amount);
                player.updateSingleStat(MapleStat.INT, player.getStat().getInt());
                break;
            case 4: // LUK
                player.getStat().setLuk(player.getStat().getLuk() + amount);
                player.updateSingleStat(MapleStat.LUK, player.getStat().getLuk());
                break;
            case 5:
                player.setStorageAp(player.getStorageAp() + amount);
                break;
        }
        if (!player.isAdmin()) {
            player.setRemainingAp(player.getRemainingAp() - amount);
        }
        player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
    }

    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {

        MapleCharacter player = c.getPlayer();
        if (splitted[0].equals("storeap") || splitted[0].equals("str") || splitted[0].equals("dex") || splitted[0].equals("int") || splitted[0].equals("luk")) {
            if (splitted.length != 2) {
                return;
            }
            short x, max = 32767;
            try {
                x = Short.parseShort(splitted[1]);
            } catch (NumberFormatException asd) {
                return;
            }
            if (x > 0 && x <= player.getRemainingAp() && x < Short.MAX_VALUE) {
                if (splitted[0].equals("str") && x + player.getStat().getStr() <= max) {
                    addAP(c, (byte) 1, x);
                } else if (splitted[0].equals("dex") && x + player.getStat().getDex() <= max) {
                    addAP(c, (byte) 2, x);
                } else if (splitted[0].equals("int") && x + player.getStat().getInt() <= max) {
                    addAP(c, (byte) 3, x);
                } else if (splitted[0].equals("luk") && x + player.getStat().getLuk() <= max) {
                    addAP(c, (byte) 4, x);
                } else if (splitted[0].equalsIgnoreCase("storeap")) {
                    addAP(c, (byte) 5, x);
                } else {
                    mc.dropMessage("Make sure the stat you are trying to raise will not be over 30000.");
                }
            } else {
                mc.dropMessage("Please make sure your AP is valid.");
            }
        } else if (splitted[0].equals("reducestr") || splitted[0].equals("reducedex") || splitted[0].equals("reduceint") || splitted[0].equals("reduceluk")) {
            if (splitted.length != 2) {
                return;
            }
            short x;
            try {
                x = Short.parseShort(splitted[1]);
            } catch (NumberFormatException asd) {
                return;
            }
            if (x > 0 && x < Short.MAX_VALUE && x + player.getRemainingAp() < Short.MAX_VALUE) {
                if (splitted[0].equals("reducestr") && player.getStat().getStr() - x >= 4) {
                    reduceAP(c, (byte) 1, x);
                } else if (splitted[0].equals("reducedex") && player.getStat().getDex() - x >= 4) {
                    reduceAP(c, (byte) 2, x);
                } else if (splitted[0].equals("reduceint") && player.getStat().getInt() - x >= 4) {
                    reduceAP(c, (byte) 3, x);
                } else if (splitted[0].equals("reduceluk") && player.getStat().getLuk() - x >= 4) {
                    reduceAP(c, (byte) 4, x);
                } else {
                    mc.dropMessage("Make sure the stat you are trying to raise will not be over 30000.");
                }
            } else {
                mc.dropMessage("Please make sure your AP is valid.");
            }
        } else if (splitted[0].equalsIgnoreCase("resetstr")) {
            resetAP(c, (byte) 1);
        } else if (splitted[0].equalsIgnoreCase("resetdex")) {
            resetAP(c, (byte) 2);
        } else if (splitted[0].equalsIgnoreCase("resetint")) {
            resetAP(c, (byte) 3);
        } else if (splitted[0].equalsIgnoreCase("resetluk")) {
            resetAP(c, (byte) 4);
        } else if (splitted[0].equalsIgnoreCase("autoapon")) {
            byte x = 0;
            String msg = "You have activated Auto Ap. From now your Ap will be automagically added to your " + splitted[1];
            if (splitted[1].equalsIgnoreCase("str")) {
                x = 1;
            } else if (splitted[1].equalsIgnoreCase("dex")) {
                x = 2;
            } else if (splitted[1].equalsIgnoreCase("int")) {
                x = 3;
            } else if (splitted[1].equalsIgnoreCase("luk")) {
                x = 4;
            } else if (splitted[1].equalsIgnoreCase("storage")) {
                x = 4;
            } else {
                x = 4;
                msg = splitted[1] + " is not a valid option. Your auto ap has been set to auto add in storage Ap";
            }
            c.getPlayer().setAutoAp(x);
            mc.dropMessage(msg);
        } else if (splitted[0].equalsIgnoreCase("autoapoff")) {
            c.getPlayer().setAutoAp((byte) 0);
            mc.dropMessage("You have turned off Auto Ap");
        } else if (splitted[0].equalsIgnoreCase("restoreap")) {
            if(player.getGMSMode() > 0){
                player.dropMessage("[KrystleCruz]You cannot claim your AP when you are in GMS mode");
                return;
            }
            Short amount = 0;
            try {
                amount = Short.parseShort(splitted[1]);
            } catch (NumberFormatException numberFormatException) {
            }
            if (amount <= player.getStorageAp()) {
                claimAp(player, amount);
                mc.dropMessage("You have retreived " + amount + " Ap from your storage");
            } else {
                player.dropMessage("[KrystleCruz]You do not have enough Storage Ap. Now die for trying to scam");
                player.torture("Trying to scam Ap");
            }
        } else {
            mc.dropMessage(splitted[0] + " is not a valid command");
        }
    }

    public DonatorCommandDefinition[] getDefinition() {
        return new DonatorCommandDefinition[]{
                    new DonatorCommandDefinition("str", "<amount>", "Adds Str if you have AP"),
                    new DonatorCommandDefinition("dex", "<amount>", "Adds Dex if you have AP"),
                    new DonatorCommandDefinition("int", "<amount>", "Adds Int if you have AP"),
                    new DonatorCommandDefinition("luk", "<amount>", "Adds luk if you have AP"),
                    new DonatorCommandDefinition("reducestr", "<amount>", "reduces AP from your STR and adds to remaining AP"),
                    new DonatorCommandDefinition("reducedex", "<amount>", "reduces AP from your DEX and adds to remaining AP"),
                    new DonatorCommandDefinition("reduceint", "<amount>", "reduces AP from your INT and adds to remaining AP"),
                    new DonatorCommandDefinition("reduceluk", "<amount>", "reduces AP from your LUK and adds to remaining AP"),
                    new DonatorCommandDefinition("resetstr", "", "resets AP from your STR and adds to remaining AP"),
                    new DonatorCommandDefinition("resetdex", "", "resets AP from your DEX and adds to remaining AP"),
                    new DonatorCommandDefinition("resetint", "", "resets AP from your INT and adds to remaining AP"),
                    new DonatorCommandDefinition("resetluk", "", "resets AP from your LUK and adds to remaining AP"),
                    new DonatorCommandDefinition("storeap", "amount", "adds ap to apstorage"),
                    new DonatorCommandDefinition("restoreap", "amount", "retrieves ap from storage"),
                    new DonatorCommandDefinition("autoapon", "", "turns on auto ap"),
                    new DonatorCommandDefinition("autoapoff", "", "turns off auto ap"),};
    }
}
