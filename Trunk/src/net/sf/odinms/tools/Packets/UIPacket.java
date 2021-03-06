/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
                       Matthias Butz <matze@odinms.de>
                       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation. You may not use, modify
    or distribute this program under any other version of the
    GNU Affero General Public License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.sf.odinms.tools.Packets;


import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.SendPacketOpcode;
import net.sf.odinms.tools.data.output.MaplePacketLittleEndianWriter;

public class UIPacket {

    /*public static final MaplePacket EarnTitleMsg(final String msg) {
    final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    // "You have acquired the Pig's Weakness skill."
    mplew.writeShort(SendPacketOpcode.EARN_TITLE_MSG);
    mplew.writeMapleAsciiString(msg);

    return mplew.getPacket();
    }*/

    public static MaplePacket getStatusMsg(int itemid) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	// Temporary transformed as a dragon, even with the skill ......
	mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
	mplew.write(7);
	mplew.writeInt(itemid);

	return mplew.getPacket();
    }

    public static final MaplePacket MapNameDisplay(final int mapid) {
	final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.BOSS_ENV);
	mplew.write(0x3);
	mplew.writeMapleAsciiString("maplemap/enter/" + mapid);

	return mplew.getPacket();
    }

    public static final MaplePacket Aran_Start() {
	final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.BOSS_ENV);
	mplew.write(0x4);
	mplew.writeMapleAsciiString("Aran/balloon");

	return mplew.getPacket();
    }

    public static final MaplePacket AranTutInstructionalBalloon(final String data) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
	mplew.write(0x17);
	mplew.writeMapleAsciiString(data);
	mplew.writeInt(1);

	return mplew.getPacket();
    }

    public static final MaplePacket ShowWZEffect(final String data) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
	mplew.write(0x14);
	mplew.writeMapleAsciiString(data);

	return mplew.getPacket();
    }

    /*public static MaplePacket summonHelper(boolean summon) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SUMMON_HINT);
    mplew.write(summon ? 1 : 0);

    return mplew.getPacket();
    }*/

    /*public static MaplePacket summonMessage(int type) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SUMMON_HINT_MSG);
    mplew.write(1);
    mplew.writeInt(type);
    mplew.writeInt(7000); // probably the delay

    return mplew.getPacket();
    }*/

    /*public static MaplePacket summonMessage(String message) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SUMMON_HINT_MSG);
    mplew.write(0);
    mplew.writeMapleAsciiString(message);
    mplew.writeInt(200); // IDK
    mplew.writeShort(0);
    mplew.writeInt(10000); // Probably delay

    return mplew.getPacket();
    }*/

    public static MaplePacket IntroLock(boolean enable) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.CYGNUS_INTRO_LOCK);
	mplew.write(enable ? 1 : 0);

	return mplew.getPacket();
    }

    public static MaplePacket IntroDisableUI(boolean enable) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.CYGNUS_INTRO_DISABLE_UI);
	mplew.write(enable ? 1 : 0);

	return mplew.getPacket();
    }

  /*  public static MaplePacket fishingUpdate(byte type, int id) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.FISHING_BOARD_UPDATE);
	mplew.write(type);
	mplew.writeInt(id);

	return mplew.getPacket();
    }

    public static MaplePacket fishingCaught(int chrid) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.FISHING_CAUGHT);
	mplew.writeInt(chrid);

	return mplew.getPacket();
    }*/
}
