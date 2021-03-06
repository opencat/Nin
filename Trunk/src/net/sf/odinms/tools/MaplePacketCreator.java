/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc> 
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
package net.sf.odinms.tools;

import java.awt.Point;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


import net.sf.odinms.client.BuddylistEntry;
import net.sf.odinms.client.Buffs.MapleBuffStat;
import net.sf.odinms.client.Buffs.MapleDisease;
import net.sf.odinms.client.Buffs.MapleStat;
import net.sf.odinms.client.Inventory.IItem;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.Inventory.MapleInventoryType;
import net.sf.odinms.client.Inventory.MaplePet;
import net.sf.odinms.client.MapleQuestStatus;

import net.sf.odinms.client.Inventory.IEquip.ScrollResult;
import net.sf.odinms.client.Inventory.MapleRing;
import net.sf.odinms.client.Skills.SkillMacro;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.ByteArrayMaplePacket;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.SendPacketOpcode;
import net.sf.odinms.net.world.MapleParty;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.net.world.PartyOperation;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MapleShopItem;
import net.sf.odinms.server.MapleTrade;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.life.MapleNPC;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleReactor;
import net.sf.odinms.server.movement.LifeMovementFragment;
import net.sf.odinms.tools.data.output.LittleEndianWriter;
import net.sf.odinms.tools.data.output.MaplePacketLittleEndianWriter;
import net.sf.odinms.net.world.guild.*;
import net.sf.odinms.client.Buffs.MapleStatEffect;
import net.sf.odinms.client.Inventory.MapleMount;
import net.sf.odinms.client.MapleKeyLayout;
import net.sf.odinms.server.MapleDueyActions;
import net.sf.odinms.server.constants.InventoryConstants;
import net.sf.odinms.server.life.MapleNPCStats;
import net.sf.odinms.server.life.MobSkill;
import net.sf.odinms.server.life.SummonAttackEntry;
import net.sf.odinms.server.maps.MapleMapItem;
import net.sf.odinms.server.maps.MapleMist;
import net.sf.odinms.server.maps.MapleSummon;
import net.sf.odinms.server.shops.MaplePlayerShop;
import net.sf.odinms.tools.Packets.PacketHelper;

/**
 * Provides all MapleStory packets needed in one place.
 * 
 * @author Frz
 * @since Revision 259
 * @version 1.0
 */
public class MaplePacketCreator {

    private final static byte[] CHAR_INFO_MAGIC = new byte[]{(byte) 0xff, (byte) 0xc9, (byte) 0x9a, 0x3b};
    private final static byte[] ITEM_MAGIC = new byte[]{(byte) 0x80, 5};
    public static final List<Pair<MapleStat, Integer>> EMPTY_STATUPDATE = Collections.emptyList();

    /**
     * Gets a packet telling the client the IP of the new channel.
     *
     * @param inetAddr The InetAddress of the requested channel server.
     * @param port The port the channel is on.
     * @return The server IP packet.
     */
    public static MaplePacket getChannelChange(InetAddress inetAddr, int port) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CHANGE_CHANNEL);
        mplew.write(1);
        byte[] addr = inetAddr.getAddress();
        mplew.write(addr);
        mplew.writeShort(port);
        return mplew.getPacket();
    }

    /**
     * Gets character info for a character.
     *
     * @param chr The character to get info about.
     * @return The character info packet.
     */
    public static MaplePacket getCharInfo(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WARP_TO_MAP); // 0x49
        mplew.writeInt(chr.getClient().getChannel() - 1);
        mplew.write(1);
        mplew.write(1);
        mplew.writeShort(0);
        for (int i = 0; i < 3; i++) {
            mplew.writeInt(Randomizer.nextInt());
        }
        mplew.writeLong(-1);
        PacketHelper.addCharStats(mplew, chr);
        mplew.write(chr.getBuddylist().getCapacity()); // buddylist capacity
        PacketHelper.addInventoryInfo(mplew, chr);
        PacketHelper.addSkillInfo(mplew, chr);
        PacketHelper.addQuestInfo(mplew, chr);
        addRingInfo(mplew, chr);
        for (int x = 0; x < 15; x++) {
            mplew.write(CHAR_INFO_MAGIC);
        }
        addMonsterBookInfo(mplew, chr);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeLong(PacketHelper.getTime((long) System.currentTimeMillis()));
        return mplew.getPacket();
    }

    public static void addRingInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeShort(0);
        mplew.writeShort(chr.getCrushRings().size());
        for (MapleRing ring : chr.getCrushRings()) {
            mplew.writeInt(ring.getPartnerChrId());
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(ring.getPartnerName(), '\0', 13));
            mplew.writeInt(ring.getRingId());
            mplew.writeInt(0);
            mplew.writeInt(ring.getPartnerRingId());
            mplew.writeInt(0);
        }
        mplew.writeShort(chr.getFriendshipRings().size());
        for (MapleRing ring : chr.getFriendshipRings()) {
            mplew.writeInt(ring.getPartnerChrId());
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(ring.getPartnerName(), '\0', 13));
            mplew.writeInt(ring.getRingId());
            mplew.writeInt(0);
            mplew.writeInt(ring.getPartnerRingId());
            mplew.writeInt(0);
            mplew.writeInt(ring.getItemId());
        }
        mplew.writeShort(chr.getMarriageRings().size());
        int marriageId = 30000;
        for (MapleRing ring : chr.getMarriageRings()) {
            mplew.writeInt(marriageId);
            mplew.writeInt(chr.getGender() == 0 ? chr.getId() : ring.getPartnerChrId());
            mplew.writeInt(chr.getGender() == 0 ? ring.getPartnerChrId() : chr.getId());
            mplew.writeShort(3);
            mplew.writeInt(ring.getItemId());
            mplew.writeInt(ring.getItemId());
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(chr.getGender() == 0 ? chr.getName() : ring.getPartnerName(), '\0', 13));
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(chr.getGender() == 0 ? ring.getPartnerName() : chr.getName(), '\0', 13));
            marriageId++;
        }

    }

    private static void addMonsterBookInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        //  mplew.writeInt(chr.getMonsterBookCover()); // cover
        mplew.writeInt(0);
        mplew.write(0);
        mplew.writeShort(0);
        // Map<Integer, Integer> cards = chr.getMonsterBook().getCards();
        // mplew.writeShort(cards.size());
        // for (Entry<Integer, Integer> all : cards.entrySet()) {
        //   mplew.writeShort(all.getKey() % 10000); // Id
        // mplew.write(all.getValue()); // Level
        // }
    }

    /**
     * Gets an empty stat update.
     *
     * @return The empy stat update packet.
     */
    public static MaplePacket enableActions() {
        return updatePlayerStats(EMPTY_STATUPDATE, true);
    }

    /**
     * Gets an update for specified stats.
     *
     * @param stats The stats to update.
     * @return The stat update packet.
     */
    public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats) {
        return updatePlayerStats(stats, false);
    }

    /**
     * Gets an update for specified stats.
     *
     * @param stats The list of stats to update.
     * @param itemReaction Result of an item reaction(?)
     * @param pet Result of spawning a pet(?)
     * @return The stat update packet.
     */
    public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats, boolean itemReaction) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_STATS);
        if (itemReaction) {
            mplew.write(1);
        } else {
            mplew.write(0);
        }
        int updateMask = 0;
        for (Pair<MapleStat, Integer> statupdate : stats) {
            updateMask |= statupdate.getLeft().getValue();
        }
        List<Pair<MapleStat, Integer>> mystats = stats;
        if (mystats.size() > 1) {
            Collections.sort(mystats, new Comparator<Pair<MapleStat, Integer>>() {

                @Override
                public int compare(Pair<MapleStat, Integer> o1, Pair<MapleStat, Integer> o2) {
                    int val1 = o1.getLeft().getValue();
                    int val2 = o2.getLeft().getValue();
                    return (val1 < val2 ? -1 : (val1 == val2 ? 0 : 1));
                }
            });
        }
        mplew.writeInt(updateMask);
        for (Pair<MapleStat, Integer> statupdate : mystats) {
            if (statupdate.getLeft().getValue() >= 1) {
                if (statupdate.getLeft().getValue() == 0x1) {
                    mplew.writeShort(statupdate.getRight().shortValue());
                } else if (statupdate.getLeft().getValue() <= 0x4) {
                    mplew.writeInt(statupdate.getRight());
                } else if (statupdate.getLeft().getValue() < 0x20) {
                    mplew.write(statupdate.getRight().shortValue());
                } else if (statupdate.getLeft().getValue() < 0xFFFF) {
                    mplew.writeShort(statupdate.getRight().shortValue());
                } else {
                    mplew.writeInt(statupdate.getRight().intValue());
                }
            }
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to change maps.
     *
     * @param to The <code>MapleMap</code> to warp to.
     * @param spawnPoint The spawn portal number to spawn at.
     * @param chr The character warping to <code>to</code>
     * @return The map change packet.
     */
    public static MaplePacket getWarpToMap(MapleMap to, int spawnPoint, MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WARP_TO_MAP); // 0x49

        mplew.writeInt(chr.getClient().getChannel() - 1);
        mplew.writeShort(0x2);
        mplew.writeShort(0);
        mplew.writeInt(to.getId());
        mplew.write(spawnPoint);
        mplew.writeShort(chr.getStat().getHp()); // hp (???)

        mplew.write(0);
        long questMask = 0x1ffffffffffffffL;
        mplew.writeLong(questMask);

        return mplew.getPacket();
    }

    /**
     * Gets a packet to spawn a portal.
     *
     * @param townId The ID of the town the portal goes to.
     * @param targetId The ID of the target.
     * @param pos Where to put the portal.
     * @return The portal spawn packet.
     */
    public static MaplePacket spawnPortal(int townId, int targetId, Point pos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL);
        mplew.writeInt(townId);
        mplew.writeInt(targetId);
        if (pos != null) {
            mplew.writeShort(pos.x);
            mplew.writeShort(pos.y);
        }
        return mplew.getPacket();
    }

    /**
     * Gets a packet to spawn a door.
     *
     * @param oid The door's object ID.
     * @param pos The position of the door.
     * @param town
     * @return The remove door packet.
     */
    public static MaplePacket spawnDoor(int oid, Point pos, boolean town) {
        // [D3 00] [01] [93 AC 00 00] [6B 05] [37 03]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SPAWN_DOOR);

        mplew.write(town ? 1 : 0);
        mplew.writeInt(oid);
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);

        return mplew.getPacket();
    }

    /**
     * Gets a packet to remove a door.
     *
     * @param oid The door's ID.
     * @param town
     * @return The remove door packet.
     */
    public static MaplePacket removeDoor(int oid, boolean town) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        if (town) {
            mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL);
            mplew.writeInt(999999999);
            mplew.writeInt(999999999);
        } else {
            mplew.writeShort(SendPacketOpcode.REMOVE_DOOR);
            mplew.write(/*town ? 1 : */0);
            mplew.writeInt(oid);
        }
        return mplew.getPacket();
    }

    public static MaplePacket spawnSummon(MapleSummon summon, int skillLevel, boolean animated) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_SPECIAL_MAPOBJECT);
        mplew.writeInt(summon.getOwnerId());
        mplew.writeInt(summon.getObjectId());
        mplew.writeInt(summon.getSkill());
        mplew.write(skillLevel);
        mplew.writePos(summon.getPosition());
        mplew.write(0);
        mplew.writeShort(0);
        mplew.write(summon.getMovementType().getValue()); // 0 = don't move, 1 = follow (4th mage summons?), 2/4 = only tele follow, 3 = bird follow
        mplew.write(summon.isPuppet() ? 0 : 1); // 0 = Summon can't attack - but puppets don't attack with 1 either ^.-
        mplew.write(animated ? 0 : 1);

        return mplew.getPacket();
    }

    public static MaplePacket removeSummon(MapleSummon summon, boolean animated) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_SPECIAL_MAPOBJECT);
        mplew.writeInt(summon.getOwnerId());
        mplew.writeInt(summon.getObjectId());
        mplew.write(animated ? 4 : 1);

        return mplew.getPacket();
    }

    /**
     * Adds expiration time info to an existing MaplePacketLittleEndianWriter.
     *
     * @param mplew The MaplePacketLittleEndianWriter to write to.
     * @param time The expiration time.
     * @param showexpirationtime Show the expiration time?
     */
    private static final void addExpirationTime(MaplePacketLittleEndianWriter mplew, long time, boolean showexpirationtime) {
        mplew.writeInt(KoreanDateUtil.getItemTimestamp(time));
        mplew.write(showexpirationtime ? 1 : 2);
    }

    private static final void addItemHasExpiryTime(final MaplePacketLittleEndianWriter mplew, final long time) {
        if (time != -1) {
            mplew.writeInt(KoreanDateUtil.getItemTimestamp(time));
            mplew.write(1);
        } else {
            mplew.writeInt(400967355);
            mplew.write(2);
        }
    }

    /**
     * Gets the response to a relog request.
     *
     * @return The relog response packet.
     */
    /**
     * Gets a server message packet.
     *
     * @param message The message to convey.
     * @return The server message packet.
     */
    public static MaplePacket serverMessage(String message) {
        return serverMessage(4, 0, message, true, false);
    }

    /**
     * Gets a server notice packet.
     *
     * Possible values for <code>type</code>:<br>
     * 0: [Notice]<br>
     * 1: Popup<br>
     * 2: Light blue background and lolwhut<br>
     * 4: Scrolling message at top<br>
     * 5: Pink Text<br>
     * 6: Lightblue Text
     *
     * @param type The type of the notice.
     * @param message The message to convey.
     * @return The server notice packet.
     */
    public static MaplePacket serverNotice(int type, String message) {
        return serverMessage(type, 0, message, false, false);
    }

    /**
     * Gets a server notice packet.
     *
     * Possible values for <code>type</code>:<br>
     * 0: [Notice]<br>
     * 1: Popup<br>
     * 2: Light blue background and lolwhut<br>
     * 4: Scrolling message at top<br>
     * 5: Pink Text<br>
     * 6: Lightblue Text
     *
     * @param type The type of the notice.
     * @param channel The channel this notice was sent on.
     * @param message The message to convey.
     * @return The server notice packet.
     */
    public static MaplePacket serverNotice(int type, int channel, String message) {
        return serverMessage(type, channel, message, false, false);
    }

    public static MaplePacket serverNotice(int type, int channel, String message, boolean ear) {
        return serverMessage(type, channel, message, false, ear);
    }

    /**
     * Gets a server message packet.
     *
     * Possible values for <code>type</code>:<br>
     * 0: [Notice]<br>
     * 1: Popup<br>
     * 2: Megaphone<br>
     * 3: Super Megaphone<br>
     * 4: Scrolling message at top<br>
     * 5: Pink Text<br>
     * 6: Lightblue Text
     * 8: Item megaphone
     * 9: Heart megaphone
     * 10: Skull Super megaphone
     * 11: Green megaphone message?
     * 12: Three line of megaphone text
     * 13: End of file =.="
     * 14: Green Gachapon box
     * 15: Red Gachapon box
     *
     * @param type The type of the notice.
     * @param channel The channel this notice was sent on.
     * @param message The message to convey.
     * @param servermessage Is this a scrolling ticker?
     * @return The server notice packet.
     */
    private static MaplePacket serverMessage(int type, int channel, String message, boolean servermessage, boolean megaEar) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE);
        mplew.write(type);
        if (servermessage) {
            mplew.write(1);
        }
        mplew.writeMapleAsciiString(message);
        switch (type) {
            case 3:
            case 9:
            case 10:
                mplew.write(channel - 1); // channel
                mplew.write(megaEar ? 1 : 0);
                break;
            case 6:
                mplew.writeInt(0);
                break;
        }
        return mplew.getPacket();
    }

    /**
     * Gets a server message packet.
     *
     * Possible values for <code>type</code>:<br />
     * 0: Megaphone<br />
     * 1: Supermegaphone<br />
     *
     *
     * @param type The type of the notice.
     * @param channel The channel this notice was sent on.
     * @param message The message to convey.
     * @param servermessage Is this a scrolling ticker?
     * @return The server notice packet.
     */
    public static MaplePacket itemMegaphone(String msg, boolean whisper, int channel, IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE);
        mplew.write(8);
        mplew.writeMapleAsciiString(msg);
        mplew.write(channel - 1);
        mplew.write(whisper ? 1 : 0);

        if (item == null) {
            mplew.write(0);
        } else {
            PacketHelper.addItemInfo(mplew, item, false, false);
        }
        return mplew.getPacket();
    }

    public static MaplePacket tripleSmega(List<String> message, boolean ear, int channel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE);
        mplew.write(0x0A);

        if (message.get(0) != null) {
            mplew.writeMapleAsciiString(message.get(0));
        }
        mplew.write(message.size());
        for (int i = 1; i < message.size(); i++) {
            if (message.get(i) != null) {
                mplew.writeMapleAsciiString(message.get(i));
            }
        }
        mplew.write(channel - 1);
        mplew.write(ear ? 1 : 0);

        return mplew.getPacket();
    }

    /**
     * Gets an avatar megaphone packet.
     *
     * @param chr The character using the avatar megaphone.
     * @param channel The channel the character is on.
     * @param itemId The ID of the avatar-mega.
     * @param message The message that is sent.
     * @return The avatar mega packet.
     */
    public static MaplePacket getAvatarMega(MapleCharacter chr, int channel, int itemId, String message, boolean ear) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.AVATAR_MEGA);
        mplew.writeInt(itemId);
        mplew.writeMapleAsciiString(chr.getName());
        mplew.writeMapleAsciiString(message);
        mplew.writeInt(channel - 1); // channel
        mplew.write(ear ? 1 : 0);
        PacketHelper.addCharLook(mplew, chr, true);
        return mplew.getPacket();
    }

    /**
     * Gets a NPC spawn packet.
     *
     * @param life The NPC to spawn.
     * @param requestController Does the NPC want a controller?
     * @return The NPC spawn packet.
     */
    public static MaplePacket spawnNPC(MapleNPC life, boolean requestController) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        // Request
        // E5 00 [01] [7D 01 00 00] [24 71 0F 00] [2B 0D] [8C FF] [01] [3F 00] [F9 0C] [5D 0D] [01]
        // Spawn
        // E3 00 [7E 01 00 00] [44 DB 8A 00] [00 02] [9A 00] [01] [77 00] [CE 01] [32 02] [01]
        if (requestController) {
            mplew.writeShort(SendPacketOpcode.SPAWN_NPC_REQUEST_CONTROLLER);
            mplew.write(1); // ?

        } else {
            mplew.writeShort(SendPacketOpcode.SPAWN_NPC);
        }
        mplew.writeInt(life.getObjectId());
        mplew.writeInt(life.getId());
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getCy());
        mplew.write(life.getF() == 1 ? 0 : 1); //Facing Left

        mplew.writeShort(life.getFh());
        mplew.writeShort(life.getRx0());
        mplew.writeShort(life.getRx1());

        mplew.write(1);

        return mplew.getPacket();
    }

    /**
     * Handles spawning monsters that spawn after another is killed
     * @param life The mob to spawn
     * @param parent The OID of the parent mob
     * @return The packet to spawn the mob
     */
    public static MaplePacket spawnRevives(MapleMonster life, int parent) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER);
        mplew.writeInt(life.getObjectId());
        mplew.write(1);
        mplew.writeInt(life.getId());
        mplew.write(0); // Status

        mplew.writeShort(0);
        mplew.write(8);
        mplew.writeInt(0);
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getPosition().y);
        mplew.write(life.getStance());
        mplew.writeShort(life.getFh());
        mplew.writeShort(life.getStartFh());
        mplew.write(0xFD); // FD

        mplew.writeInt(parent); // oid of the mob that spawned it

        mplew.writeShort(-1);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    /**
     * Handles monsters not being targettable, such as Zakum's first body.
     * @param life The mob to spawn as non-targettable.
     * @param effect The effect to show when spawning.
     * @return The packet to spawn the mob as non-targettable.
     */
    public static MaplePacket spawnFakeMonster(MapleMonster life, int effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL);

        mplew.write(1);
        mplew.writeInt(life.getObjectId());
        mplew.write(5);
        mplew.writeInt(life.getId());
        mplew.writeInt(0);
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getPosition().y);
        mplew.write(life.getStance());
        mplew.writeShort(life.getStartFh());
        mplew.writeShort(life.getFh());

        if (effect > 0) {
            mplew.write(effect);
            mplew.write(0);
            mplew.writeShort(0);
        }

        mplew.writeShort(-2);

        mplew.writeInt(0);

        return mplew.getPacket();
    }

    /**
     * Gets a response to a move monster packet.
     *
     * @param objectid The ObjectID of the monster being moved.
     * @param moveid The movement ID.
     * @param currentMp The current MP of the monster.
     * @param useSkills Can the monster use skills?
     * @param skillId The skill ID for the monster to use.
     * @param skillLevel The level of the skill to use.
     * @return The move response packet.
     */
    public static MaplePacket moveMonsterResponse(int objectid, short moveid, int currentMp, boolean useSkills, int skillId, int skillLevel) {
        // A1 00 18 DC 41 00 01 00 00 1E 00 00 00
        // A1 00 22 22 22 22 01 00 00 00 00 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MOVE_MONSTER_RESPONSE);
        mplew.writeInt(objectid);
        mplew.writeShort(moveid);
        mplew.write(useSkills ? 1 : 0);
        mplew.writeShort(currentMp);
        mplew.write(skillId);
        mplew.write(skillLevel);

        return mplew.getPacket();
    }

    /**
     * Gets a general chat packet.
     *
     * @param cidfrom The character ID who sent the chat.
     * @param text The text of the chat.
     * @return The general chat packet.
     */
    public static MaplePacket getChatText(int cidfrom, String text, boolean whiteBG, int show) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CHATTEXT);
        mplew.writeInt(cidfrom);
        mplew.write(whiteBG ? 1 : 0);
        mplew.writeMapleAsciiString(text);
        mplew.write(show);
        return mplew.getPacket();
    }

    /**
     * For testing only! Gets a packet from a hexadecimal string.
     *
     * @param hex The hexadecimal packet to create.
     * @return The MaplePacket representing the hex string.
     */
    public static MaplePacket getPacketFromHexString(String hex) {
        byte[] b = HexTool.getByteArrayFromHexString(hex);
        return new ByteArrayMaplePacket(b);
    }

    /**
     * Gets a packet telling the client to show an EXP increase.
     *
     * @param gain The amount of EXP gained.
     * @param inChat In the chat box?
     * @param white White text or yellow?
     * @return The exp gained packet.
     */
    public static MaplePacket getShowExpGain(int gain, boolean inChat, boolean white, short party) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(3); // 3 = exp, 4 = fame, 5 = mesos, 6 = guildpoints
        mplew.write(white ? 1 : 0);
        mplew.writeInt(gain);
        mplew.write(inChat ? 1 : 0);
        mplew.writeInt(0); // monster book bonus
        mplew.write(0);
        mplew.write(((short) party) * 10); // Party bonus
        mplew.writeShort(0);
        mplew.writeLong(0);
        mplew.writeInt(0);
        if (inChat) {
            mplew.writeInt(0); // some ring bonus/ party exp ??
        } else { // some ring bonus/ party exp
            mplew.writeShort(0);
            mplew.write(0);
        }
        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show a meso gain.
     *
     * @param gain How many mesos gained.
     * @return The meso gain packet.
     */
    public static MaplePacket getShowMesoGain(int gain) {
        return getShowMesoGain(gain, false);
    }

    /**
     * Gets a packet telling the client to show a meso gain.
     *
     * @param gain How many mesos gained.
     * @param inChat Show in the chat window?
     * @return The meso gain packet.
     */
    public static MaplePacket getShowMesoGain(int gain, boolean inChat) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        if (!inChat) {
            mplew.write(0);
            mplew.write(1);
        } else {
            mplew.write(5);
        }
        mplew.writeInt(gain);
        mplew.writeShort(0); // inet cafe meso gain ?.o

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show a item gain.
     *
     * @param itemId The ID of the item gained.
     * @param quantity How many items gained.
     * @return The item gain packet.
     */
    public static MaplePacket getShowItemGain(int itemId, short quantity) {
        return getShowItemGain(itemId, quantity, false);
    }

    /**
     * Gets a packet telling the client to show an item gain.
     *
     * @param itemId The ID of the item gained.
     * @param quantity The number of items gained.
     * @param inChat Show in the chat window?
     * @return The item gain packet.
     */
    public static MaplePacket getShowItemGain(int itemId, short quantity, boolean inChat) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        if (inChat) {
            mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
            mplew.write(3);
            mplew.write(1);
            mplew.writeInt(itemId);
            mplew.writeInt(quantity);
        } else {
            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
            mplew.writeShort(0);
            mplew.writeInt(itemId);
            mplew.writeInt(quantity);
            mplew.writeInt(0);
            mplew.writeInt(0);
        }
        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show mesos coming out of a map
     * object.
     *
     * @param amount The amount of mesos.
     * @param itemoid The ObjectID of the dropped mesos.
     * @param dropperoid The OID of the dropper.
     * @param ownerid The ID of the drop owner.
     * @param dropfrom Where to drop from.
     * @param dropto Where the drop lands.
     * @param mod ?
     * @return The drop mesos packet.
     */
    public static MaplePacket dropMesoFromMapObject(
            int amount, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod) {
        return dropItemFromMapObjectInternal(amount, itemoid, dropperoid, ownerid, ownerid == 0 ? 2 : 0, dropfrom, dropto, mod, true);
    }

    public static MaplePacket dropMesoFromMapObject(
            int amount, int itemoid, int dropperoid, int ownerid, int typadrop, Point dropfrom, Point dropto, byte mod) {
        return dropItemFromMapObjectInternal(amount, itemoid, dropperoid, ownerid, typadrop, dropfrom, dropto, mod, true);
    }

    /**
     * Gets a packet telling the client to show an item coming out of a map
     * object.
     *
     * @param itemid The ID of the dropped item.
     * @param itemoid The ObjectID of the dropped item.
     * @param dropperoid The OID of the dropper.
     * @param ownerid The ID of the drop owner.
     * @param dropfrom Where to drop from.
     * @param dropto Where the drop lands.
     * @param mod ?
     * @return The drop mesos packet.
     */
    public static MaplePacket dropItemFromMapObject(int itemid, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod) {
        return dropItemFromMapObjectInternal(itemid, itemoid, dropperoid, ownerid, ownerid == 0 ? 2 : 0, dropfrom, dropto, mod, false);
    }

    public static MaplePacket dropItemFromMapObject(int itemid, int itemoid, int dropperoid, int ownerid, int typadrop, Point dropfrom, Point dropto, byte mod) {
        return dropItemFromMapObjectInternal(itemid, itemoid, dropperoid, ownerid, typadrop, dropfrom, dropto, mod, false);
    }

    public static MaplePacket dropItemFromMapObject(MapleMapItem drop, Point dropfrom, Point dropto, byte mod) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DROP_ITEM_FROM_MAPOBJECT);
        mplew.write(mod); // 1 animation, 2 no animation, 3 spawn disappearing item [Fade], 4 spawn disappearing item
        mplew.writeInt(drop.getObjectId()); // item owner id
        mplew.write(drop.getMeso() > 0 ? 1 : 0); // 1 mesos, 0 item, 2 and above all item meso bag,
        mplew.writeInt(drop.getItemId()); // drop object ID
        mplew.writeInt(drop.getOwner()); // owner charid
        mplew.write(drop.getDropType()); // 0 = timeout for non-owner, 1 = timeout for non-owner's party, 2 = FFA, 3 = explosive/FFA
        mplew.writePos(dropto);
        mplew.writeInt(0);

        if (mod != 2) {
            mplew.writePos(dropfrom);
            mplew.writeShort(0);
        }
        if (drop.getMeso() == 0) {
            mplew.write(0);
            addItemHasExpiryTime(mplew, drop.getItem().getExpiration());
        }
        mplew.write(drop.isPlayerDrop() ? 0 : 1); // pet EQP pickup

        return mplew.getPacket();
    }

    /**
     * Internal function to get a packet to tell the client to drop an item onto
     * the map.
     *
     * @param itemid The ID of the item to drop.
     * @param itemoid The ObjectID of the dropped item.
     * @param dropperoid The OID of the dropper.
     * @param ownerid The ID of the drop owner.
     * @param dropfrom Where to drop from.
     * @param dropto Where the drop lands.
     * @param mod ?
     * @param mesos Is the drop mesos?
     * @return The item drop packet.
     */
    public static MaplePacket dropItemFromMapObjectInternal(int itemid, int itemoid, int dropperoid, int ownerid, int mode, Point dropfrom, Point dropto, byte mod, boolean mesos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DROP_ITEM_FROM_MAPOBJECT);
        mplew.write(mod);
        mplew.writeInt(itemoid);
        mplew.write(mesos ? 1 : 0);
        mplew.writeInt(itemid);
        mplew.writeInt(ownerid);
        mplew.write(mode); // DROP OWNERSHIP (0 = owner, 1 = party, 2 = FFA, 3 = explosive/FFA)
        mplew.writeShort(dropto.x);
        mplew.writeShort(dropto.y);
        if (mod != 2) {
            mplew.writeInt(ownerid);
            mplew.writeShort(dropfrom.x);
            mplew.writeShort(dropfrom.y);
        } else {
            mplew.writeInt(dropperoid);
        }
        mplew.write(0);
        if (mod != 2) {
            mplew.write(1);
            mplew.write(mesos ? 1 : 0);
        }
        if (!mesos) {
            mplew.write(ITEM_MAGIC);
            addExpirationTime(mplew, System.currentTimeMillis(), false);
            mplew.write(0);
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet spawning a player as a mapobject to other clients.
     *
     * @param chr The character to spawn to other clients.
     * @return The spawn player packet.
     */
    public static MaplePacket spawnPlayerMapobject(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SPAWN_PLAYER);
        mplew.writeInt(chr.getId());
        mplew.writeMapleAsciiString(chr.getName());
        if (chr.getGuildId() < 1) {
            mplew.writeMapleAsciiString("");
            mplew.write(new byte[6]);
        } else {
            MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
            if (gs != null) {
                mplew.writeMapleAsciiString(gs.getName());
                mplew.writeShort(gs.getLogoBG());
                mplew.write(gs.getLogoBGColor());
                mplew.writeShort(gs.getLogo());
                mplew.write(gs.getLogoColor());
            } else {
                mplew.writeMapleAsciiString("");
                mplew.write(new byte[6]);
            }
        }
        mplew.writeInt(0); //Not sure anymore this should be an int.
        mplew.write(0xf8);
        mplew.write(3);
        mplew.writeShort(0);
        if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
            mplew.writeInt(2);
        } else {
            mplew.writeInt(0);
        }
        long buffmask = 0;
        Integer buffvalue = null;
        if (chr.getBuffedValue(MapleBuffStat.DARKSIGHT) != null && !chr.isHidden()) {
            buffmask |= MapleBuffStat.DARKSIGHT.getValue();
        }
        if (chr.getBuffedValue(MapleBuffStat.COMBO) != null) {
            buffmask |= MapleBuffStat.COMBO.getValue();
            buffvalue = Integer.valueOf(chr.getBuffedValue(MapleBuffStat.COMBO).intValue());
        }
        if (chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null) {
            buffmask |= MapleBuffStat.SHADOWPARTNER.getValue();
        }
        if (chr.getBuffedValue(MapleBuffStat.SOULARROW) != null) {
            buffmask |= MapleBuffStat.SOULARROW.getValue();
        }
        if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
            buffvalue = Integer.valueOf(chr.getBuffedValue(MapleBuffStat.MORPH).intValue());
        }
        mplew.writeInt((int) ((buffmask >> 32) & 0xffffffffL));
        if (buffvalue != null) {
            if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
                mplew.writeShort(buffvalue);
            } else {
                mplew.write(buffvalue.byteValue());
            }
        }
        mplew.writeInt((int) (buffmask & 0xffffffffL));
        int CHAR_MAGIC_SPAWN = Randomizer.nextInt();
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeShort(0); //v74
        mplew.write(0); //v74
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);//v74
        mplew.writeShort(0);
        mplew.write(0); //v74
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeShort(0);
        mplew.write(0);
        IItem mount = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18);
        if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
            if (chr.getBuffSource(MapleBuffStat.MONSTER_RIDING) != 5221006) {
                mplew.writeInt(mount.getItemId());
                mplew.writeInt(chr.getBuffSource(MapleBuffStat.MONSTER_RIDING));
                mplew.writeInt(CHAR_MAGIC_SPAWN);
            } else {
                mplew.writeInt(1932000);
                mplew.writeInt(5221006);
                mplew.writeInt(CHAR_MAGIC_SPAWN);
            }
        } else {
            mplew.writeLong(0); // 2 int
            mplew.writeInt(CHAR_MAGIC_SPAWN);
        }
        mplew.write(0);
        mplew.writeLong(0); // 2 int
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeLong(0); // 2 int
        mplew.writeLong(0); // 2 int
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.write(0);
        mplew.writeLong(0); // 2 int
        mplew.writeInt(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeShort(0);
        mplew.write(0);
        if (chr.getJob() < 910 && !chr.isJounin()) {
            mplew.writeShort(412);
        } else {
            mplew.writeShort(1411);
        }
        PacketHelper.addCharLook(mplew, chr, false);
        mplew.writeInt(0);
        mplew.writeInt(chr.getItemEffect());
        mplew.writeInt(chr.getChair());
        mplew.writeShort(chr.getPosition().x);
        mplew.writeShort(chr.getPosition().y);
        mplew.write(chr.getStance());
        mplew.writeShort(0); // FH
        // pets apparently.
        mplew.writeShort(0); // announce box.
        mplew.writeInt(1);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeShort(0);
        addRingLooks(mplew, chr);
        mplew.write(0);
        return mplew.getPacket();
    }

    private static void addRingLooks(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.write(chr.getEquippedRing(0) != 0 ? 1 : 0);
        for (MapleRing ring : chr.getCrushRings()) {
            if (ring.getRingId() == chr.getEquippedRing(0)) {
                mplew.writeInt(ring.getRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getPartnerRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getItemId());
            }
        }
        mplew.write(chr.getEquippedRing(1) != 0 ? 1 : 0);
        for (MapleRing ring : chr.getFriendshipRings()) {
            if (ring.getRingId() == chr.getEquippedRing(1)) {
                mplew.writeInt(ring.getRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getPartnerRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getItemId());
            }
        }
        mplew.write(chr.getEquippedRing(2) != 0 ? 1 : 0);
        for (MapleRing ring : chr.getMarriageRings()) {
            if (ring.getRingId() == chr.getEquippedRing(2)) {
                mplew.writeInt(ring.getPartnerChrId());
                mplew.writeInt(chr.getId());
                mplew.writeInt(ring.getItemId());
            }
        }
        mplew.write(0);
    }

    /**
     * Adds a announcement box to an existing MaplePacketLittleEndianWriter.
     *
     * @param mplew The MaplePacketLittleEndianWriter to add an announcement box
     *            to.
     * @param shop The shop to announce.
     */
    private static void addAnnounceBox(MaplePacketLittleEndianWriter mplew, MaplePlayerShop shop) {
        // 00: no game
        // 01: omok game
        // 02: card game
        // 04: shop
        mplew.write(4);
        mplew.writeInt(shop.getObjectId()); // gameid/shopid
        mplew.writeMapleAsciiString(shop.getDescription()); // desc
        // 00: public
        // 01: private
        mplew.write(0);
        // 00: red 4x3
        // 01: green 5x4
        // 02: blue 6x5
        // omok:
        // 00: normal
        mplew.write(0);
        // first slot: 1/2/3/4
        // second slot: 1/2/3/4
        mplew.write(1);
        mplew.write(4);
        // 0: open
        // 1: in progress
        mplew.write(0);
    }

    public static MaplePacket facialExpression(MapleCharacter from, int expression) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FACIAL_EXPRESSION);

        mplew.writeInt(from.getId());
        mplew.writeInt(expression);

        return mplew.getPacket();
    }

    private static void serializeMovementList(LittleEndianWriter lew, List<LifeMovementFragment> moves) {
        lew.write(moves.size());
        for (LifeMovementFragment move : moves) {
            move.serialize(lew);
        }
    }

    public static MaplePacket movePlayer(int cid, List<LifeMovementFragment> moves) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MOVE_PLAYER);
        mplew.writeInt(cid);
        mplew.writeInt(0);
        serializeMovementList(mplew, moves);
        return mplew.getPacket();
    }

    public static MaplePacket moveSummon(int cid, int oid, Point startPos, List<LifeMovementFragment> moves) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MOVE_SUMMON);
        mplew.writeInt(cid);
        mplew.writeInt(oid);
        mplew.writeShort(startPos.x);
        mplew.writeShort(startPos.y);
        serializeMovementList(mplew, moves);
        return mplew.getPacket();
    }

    public static MaplePacket summonAttack(final int cid, final int summonSkillId, final byte animation, final List<SummonAttackEntry> allDamage) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SUMMON_ATTACK);
        mplew.writeInt(cid);
        mplew.writeInt(summonSkillId);
        mplew.write(animation);
        mplew.write(allDamage.size());
        for (final SummonAttackEntry attackEntry : allDamage) {
            mplew.writeInt(attackEntry.getMonster().getObjectId()); // oid
            mplew.write(6); // who knows
            mplew.writeInt(attackEntry.getDamage()); // damage
        }
        return mplew.getPacket();
    }

    public static MaplePacket closeRangeAttack(int cid, int tbyte, int skill, int level, byte display, byte animation, byte speed, List<AttackPair> damage) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CLOSE_RANGE_ATTACK);
        mplew.writeInt(cid);
        mplew.write(tbyte);
        if (skill > 0) {
            mplew.write(level);
            mplew.writeInt(skill);
        } else {
            mplew.write(0);
        }
        //mplew.write(0); // Added on v.82
        mplew.write(display);
        mplew.write(animation);
        mplew.write(speed);
        mplew.write(0); // Mastery
        mplew.writeInt(0);  // E9 03 BE FC

        if (skill == 4211006) {
            for (AttackPair oned : damage) {
                if (oned.attack != null) {
                    mplew.writeInt(oned.objectid);
                    mplew.write(0x06);
                    mplew.write(oned.attack.size());
                    for (Integer eachd : oned.attack) {
                        // highest bit set = crit
                        mplew.writeInt(eachd);
                    }
                }
            }
        } else {
            for (AttackPair oned : damage) {
                if (oned.attack != null) {
                    mplew.writeInt(oned.objectid);
                    mplew.write(0x06);
                    for (Integer eachd : oned.attack) {
                        // highest bit set = crit
                        mplew.writeInt(eachd);
                    }
                }
            }
        }
        return mplew.getPacket();
    }

    public static MaplePacket rangedAttack(int cid, byte tbyte, int skill, int level, byte display, byte animation, byte speed, int itemid, List<AttackPair> damage, final Point pos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.RANGED_ATTACK);
        mplew.writeInt(cid);
        mplew.write(tbyte);
        if (skill > 0) {
            mplew.write(level);
            mplew.writeInt(skill);
        } else {
            mplew.write(0);
        }
        //mplew.write(0); // Added on v.82
        mplew.write(display);
        mplew.write(animation);
        mplew.write(speed);
        mplew.write(0); // Mastery level, who cares
        mplew.writeInt(itemid);

        for (AttackPair oned : damage) {
            if (oned.attack != null) {
                mplew.writeInt(oned.objectid);
                mplew.write(0x06);
                for (Integer eachd : oned.attack) {
                    // highest bit set = crit
                    mplew.writeInt(eachd.intValue());
                }
            }
        }
        mplew.writePos(pos); // Position

        return mplew.getPacket();
    }

    public static MaplePacket magicAttack(int cid, int tbyte, int skill, int level, byte display, byte animation, byte speed, List<AttackPair> damage, int charge) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MAGIC_ATTACK);
        mplew.writeInt(cid);
        mplew.write(tbyte);
        mplew.write(level);
        mplew.writeInt(skill);

        //mplew.write(0); // Added on v.82
        mplew.write(display);
        mplew.write(animation);
        mplew.write(speed);
        mplew.write(0); // Mastery byte is always 0 because spells don't have a swoosh
        mplew.writeInt(0);

        for (AttackPair oned : damage) {
            if (oned.attack != null) {
                mplew.writeInt(oned.objectid);
                mplew.write(-1);
                for (Integer eachd : oned.attack) {
                    // highest bit set = crit
                    mplew.writeInt(eachd.intValue());
                }
            }
        }
        if (charge > 0) {
            mplew.writeInt(charge);
        }
        return mplew.getPacket();
    }

    public static MaplePacket getNPCShop(MapleClient c, int sid, List<MapleShopItem> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();

        mplew.writeShort(SendPacketOpcode.OPEN_NPC_SHOP);
        mplew.writeInt(sid);
        mplew.writeShort(items.size()); // item count
        for (MapleShopItem item : items) {
            mplew.writeInt(item.getItemId());
            mplew.writeInt(item.getPrice());
            if (!InventoryConstants.isThrowingStar(item.getItemId()) && !InventoryConstants.isBullet(item.getItemId())) {
                mplew.writeShort(1); // stacksize o.o
                mplew.writeShort(item.getBuyable());
            } else {
                mplew.writeShort(0);
                mplew.writeInt(0);
                // o.O getPrice sometimes returns the unitPrice not the price
                mplew.writeShort(BitTools.doubleToShortBits(ii.getPrice(item.getItemId())));
                mplew.writeShort(ii.getSlotMax(item.getItemId()));
            }
        }
        return mplew.getPacket();
    }

    /**
     * code (8 = sell, 0 = buy, 0x20 = due to an error the trade did not happen
     * o.o)
     *
     * @param code
     * @return
     */
    public static MaplePacket confirmShopTransaction(byte code) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CONFIRM_SHOP_TRANSACTION);
        mplew.write(code);
        return mplew.getPacket();
    }

    /*
     * 19 reference 00 01 00 = new while adding 01 01 00 = add from drop 00 01 01 = update count 00 01 03 = clear slot
     * 01 01 02 = move to empty slot 01 02 03 = move and merge 01 02 01 = move and merge with rest
     */
    public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item) {
        return addInventorySlot(type, item, false);
    }

    public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item, boolean fromDrop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(fromDrop ? 1 : 0);
        mplew.writeShort(1); // add mode
        mplew.write(type.getType()); // iv type
        mplew.write(item.getPosition()); // slot id
        PacketHelper.addItemInfo(mplew, item, true, false);

        return mplew.getPacket();
    }

    public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item) {
        return updateInventorySlot(type, item, false);
    }

    public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item, boolean fromDrop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        if (fromDrop) {
            mplew.write(1);
        } else {
            mplew.write(0);
        }
        mplew.write(HexTool.getByteArrayFromHexString("01 01"));
        mplew.write(type.getType());
        mplew.write(item.getPosition());
        mplew.write(0);
        mplew.writeShort(item.getQuantity());
        return mplew.getPacket();
    }

    public static MaplePacket moveInventoryItem(MapleInventoryType type, short src, short dst) {
        return moveInventoryItem(type, src, dst, (byte) -1);
    }

    public static MaplePacket moveInventoryItem(MapleInventoryType type, short src, short dst, byte equipIndicator) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(HexTool.getByteArrayFromHexString("01 01 02"));
        mplew.write(type.getType());
        mplew.writeShort(src);
        mplew.writeShort(dst);
        if (equipIndicator != -1) {
            mplew.write(equipIndicator);
        }
        return mplew.getPacket();
    }

    public static MaplePacket moveAndMergeInventoryItem(MapleInventoryType type, short src, short dst, short total) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(HexTool.getByteArrayFromHexString("01 02 03"));
        mplew.write(type.getType());
        mplew.writeShort(src);
        mplew.write(1); // merge mode?
        mplew.write(type.getType());
        mplew.writeShort(dst);
        mplew.writeShort(total);
        return mplew.getPacket();
    }

    public static MaplePacket moveAndMergeWithRestInventoryItem(MapleInventoryType type, short src, short dst,
            short srcQ, short dstQ) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(HexTool.getByteArrayFromHexString("01 02 01"));
        mplew.write(type.getType());
        mplew.writeShort(src);
        mplew.writeShort(srcQ);
        mplew.write(HexTool.getByteArrayFromHexString("01"));
        mplew.write(type.getType());
        mplew.writeShort(dst);
        mplew.writeShort(dstQ);
        return mplew.getPacket();
    }

    public static MaplePacket clearInventoryItem(MapleInventoryType type, short slot, boolean fromDrop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(fromDrop ? 1 : 0);
        mplew.write(HexTool.getByteArrayFromHexString("01 03"));
        mplew.write(type.getType());
        mplew.writeShort(slot);
        return mplew.getPacket();
    }

    public static MaplePacket updateSpecialItemUse(IItem item, byte invType) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(0); // could be from drop
        mplew.write(2); // always 2
        mplew.write(3); // quantity > 0 (?)
        mplew.write(invType); // Inventory type
        mplew.write(item.getPosition()); // item slot
        mplew.writeShort(0);
        mplew.write(invType);
        mplew.write(item.getPosition()); // wtf repeat
        PacketHelper.addItemInfo(mplew, item, true, false);

        return mplew.getPacket();
    }

    public static MaplePacket scrolledItem(IItem scroll, IItem item, boolean destroyed) {
        // 18 00 01 02 03 02 08 00 03 01 F7 FF 01
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(1); // fromdrop always true
        mplew.write(destroyed ? 2 : 3);
        mplew.write(scroll.getQuantity() > 0 ? 1 : 3);
        mplew.write(MapleInventoryType.USE.getType());
        mplew.writeShort(scroll.getPosition());
        if (scroll.getQuantity() > 0) {
            mplew.writeShort(scroll.getQuantity());
        }
        mplew.write(3);
        if (!destroyed) {
            mplew.write(MapleInventoryType.EQUIP.getType());
            mplew.writeShort(item.getPosition());
            mplew.write(0);
        }
        mplew.write(MapleInventoryType.EQUIP.getType());
        mplew.writeShort(item.getPosition());

        if (!destroyed) {
            PacketHelper.addItemInfo(mplew, item, true, true);
        }
        mplew.write(1);
        return mplew.getPacket();
    }

    public static MaplePacket getScrollEffect(int chr, ScrollResult scrollSuccess, boolean legendarySpirit) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_SCROLL_EFFECT);
        mplew.writeInt(chr);
        switch (scrollSuccess) {
            case SUCCESS:
                mplew.writeShort(1);
                mplew.writeShort(legendarySpirit ? 1 : 0);
                break;
            case FAIL:
                mplew.writeShort(0);
                mplew.writeShort(legendarySpirit ? 1 : 0);
                break;
            case CURSE:
                mplew.write(0);
                mplew.write(1);
                mplew.writeShort(legendarySpirit ? 1 : 0);
                break;
            default:
                throw new IllegalArgumentException("effect in illegal range");
        }
        return mplew.getPacket();
    }

    public static MaplePacket removePlayerFromMap(int cid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.REMOVE_PLAYER_FROM_MAP);
        // mplew.writeShort(0x65); // 47 63
        mplew.writeInt(cid);
        return mplew.getPacket();
    }

    /**
     * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
     * 4 - explode<br/> cid is ignored for 0 and 1
     *
     * @param oid
     * @param animation
     * @param cid
     * @return
     */
    public static MaplePacket removeItemFromMap(int oid, int animation, int cid) {
        return removeItemFromMap(oid, animation, cid, false, 0);
    }

    /**
     * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
     * 4 - explode<br/> cid is ignored for 0 and 1.<br /><br />Flagging pet
     * as true will make a pet pick up the item.
     *
     * @param oid
     * @param animation
     * @param cid
     * @param pet
     * @param slot
     * @return
     */
    public static MaplePacket removeItemFromMap(int oid, int animation, int cid, boolean pet) {
        return removeItemFromMap(oid, animation, cid, pet, 0);
    }

    public static MaplePacket explodeDrop(int oid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_ITEM_FROM_MAP);
        mplew.write(4); // 4 = Explode
        mplew.writeInt(oid);
        mplew.writeShort(655);

        return mplew.getPacket();
    }

    public static MaplePacket removeItemFromMap(int oid, int animation, int cid, boolean pet, int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.REMOVE_ITEM_FROM_MAP);
        mplew.write(animation); // expire
        mplew.writeInt(oid);
        if (animation >= 2) {
            mplew.writeInt(cid);
            if (pet) {
                mplew.write(slot);
            }
        }
        return mplew.getPacket();
    }

    public static MaplePacket updateCharLook(MapleCharacter chr) {
        // 88 00 80 74 03 00 01 00 00 19 50 00 00 00 67 75 00 00 02 34 71 0F 00
        // 04 59 BF 0F 00 05 AB 05 10 00 07 8C 5B
        // 10 00 08 F4 82 10 00 09 E7 D0 10 00 0A BE A9 10 00 0B 0C 05 14 00 FF
        // FF 00 00 00 00 00 00 00 00 00 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_LOOK);
        mplew.writeInt(chr.getId());
        mplew.write(1);
        PacketHelper.addCharLook(mplew, chr, false);
        mplew.write(0);
        mplew.writeShort(0);
        return mplew.getPacket();
    }

    public static MaplePacket dropInventoryItem(MapleInventoryType type, short src) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        // mplew.writeShort(0x19);
        mplew.write(HexTool.getByteArrayFromHexString("01 01 03"));
        mplew.write(type.getType());
        mplew.writeShort(src);
        if (src < 0) {
            mplew.write(1);
        }
        return mplew.getPacket();
    }

    public static MaplePacket dropInventoryItemUpdate(MapleInventoryType type, IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(HexTool.getByteArrayFromHexString("01 01 01"));
        mplew.write(type.getType());
        mplew.writeShort(item.getPosition());
        mplew.writeShort(item.getQuantity());
        return mplew.getPacket();
    }

    public static MaplePacket damagePlayer(int skill, int monsteridfrom, int cid, int damage, int fake, byte direction, int reflect, boolean is_pg, int oid, int pos_x, int pos_y) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DAMAGE_PLAYER);
        mplew.writeInt(cid);
        mplew.write(skill);
        mplew.writeInt(damage);
        mplew.writeInt(monsteridfrom);
        mplew.write(direction);

        if (reflect > 0) {
            mplew.write(reflect);
            mplew.write(is_pg ? 1 : 0);
            mplew.writeInt(oid);
            mplew.write(6);
            mplew.writeShort(pos_x);
            mplew.writeShort(pos_y);
            mplew.write(0);
        } else {
            mplew.writeShort(0);
        }
        mplew.writeInt(damage);
        if (fake > 0) {
            mplew.writeInt(fake);
        }
        return mplew.getPacket();
    }

    /**
     *
     * @param c
     * @param quest
     * @return
     */
    public static MaplePacket startQuest(MapleCharacter c, short quest) {
        // [24 00] [01] [69 08] [01 00] [00]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        // mplew.writeShort(0x21);
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(1);
        mplew.writeShort(quest);
        mplew.writeShort(1);
        mplew.write(0);
        return mplew.getPacket();
    }

    /**
     * state 0 = del ok state 12 = invalid bday
     *
     * @param cid
     * @param state
     * @return
     */
    public static MaplePacket deleteCharResponse(int cid, int state) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DELETE_CHAR_RESPONSE);
        mplew.writeInt(cid);
        mplew.write(state);
        return mplew.getPacket();
    }

    public static MaplePacket charInfo(final MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CHAR_INFO);
        // mplew.writeShort(0x31);
        mplew.writeInt(chr.getId());
        mplew.write(chr.getLevel());
        mplew.writeShort(chr.getJob());
        mplew.writeShort(chr.getFame());
        mplew.write(1); // heart red or gray
        if (chr.getGuildId() <= 0) {
            mplew.writeMapleAsciiString("-");
            mplew.writeMapleAsciiString("-");
        } else {
            final MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
            mplew.writeMapleAsciiString(gs.getName());
            final MapleAlliance alliance = chr.getGuild().getAlliance(chr.getClient());
            if (alliance == null) {
                mplew.writeMapleAsciiString("-");
            } else {
                mplew.writeMapleAsciiString(alliance.getName());
            }
        }
        mplew.write(0);

        final IItem inv = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -114);
        final int peteqid = inv != null ? inv.getItemId() : 0;

        for (final MaplePet pet : chr.getPets()) {
            if (pet.getSummoned()) {
                mplew.write(pet.getUniqueId());
                mplew.writeInt(pet.getPetItemId()); // petid
                mplew.writeMapleAsciiString(pet.getName());
                mplew.write(pet.getLevel()); // pet level
                mplew.writeShort(pet.getCloseness()); // pet closeness
                mplew.write(pet.getFullness()); // pet fullness
                mplew.writeShort(0);
                mplew.writeInt(peteqid);
            }
        }
        mplew.write(0); // End of pet
        if (chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -22) != null) {
            final MapleMount mount = chr.getMount();
            mplew.write(1);
            mplew.writeInt(mount.getLevel());
            mplew.writeInt(mount.getExp());
            mplew.writeInt(mount.getFatigue());
        } else {
            mplew.write(0);
        }

        final int wishlistSize = chr.getWishlistSize();
        mplew.write(wishlistSize);
        if (wishlistSize > 0) {
            final int[] wishlist = chr.getWishlist();
            for (int i = 0; i < wishlistSize; i++) {
                mplew.writeInt(wishlist[i]);
            }
        }

        chr.getMonsterBook().addCharInfoPacket(chr.getMonsterBookCover(), mplew);

        return mplew.getPacket();
    }

    /**
     *
     * @param c
     * @param quest
     * @return
     */
    public static MaplePacket forfeitQuest(MapleCharacter c, short quest) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(1);
        mplew.writeShort(quest);
        mplew.writeShort(0);
        mplew.write(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    /**
     *
     * @param c
     * @param quest
     * @return
     */
    public static MaplePacket completeQuest(MapleCharacter c, short quest) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        /*  mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(1);
        mplew.writeShort(quest);
        mplew.write(HexTool.getByteArrayFromHexString("02 A0 67 B9 DA 69 3A C8 01"));
        mplew.writeInt(0);
        mplew.writeInt(0);
        return mplew.getPacket();*/
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(1);
        mplew.writeShort(quest);
        mplew.write(2);
        mplew.writeLong(PacketHelper.getTime(System.currentTimeMillis()));
        return mplew.getPacket();
    }

    /**
     *
     * @param c
     * @param quest
     * @param npc
     * @param progress
     * @return
     */
    // frz note, 0.52 transition: this is only used when starting a quest and
    // seems to have no effect, is it needed?
    public static MaplePacket updateQuestInfo(MapleCharacter c, short quest, int npc, byte progress) {
        // [A5 00] [08] [69 08] [86 71 0F 00] [00 00 00 00]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_QUEST_INFO);
        mplew.write(progress);
        mplew.writeShort(quest);
        mplew.writeInt(npc);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    private static long getLongMask(List<Pair<MapleBuffStat, Integer>> statups) {
        long mask = 0;
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            mask |= statup.getLeft().getValue();
        }
        return mask;
    }

    private static long getLongMaskFromList(List<MapleBuffStat> statups) {
        long mask = 0;
        for (MapleBuffStat statup : statups) {
            mask |= statup.getValue();
        }
        return mask;
    }

    private static long getLongMaskD(List<Pair<MapleDisease, Integer>> statups) {
        long mask = 0;
        for (Pair<MapleDisease, Integer> statup : statups) {
            mask |= statup.getLeft().getValue();
        }
        return mask;
    }

    private static long getLongMaskFromListD(List<MapleDisease> statups) {
        long mask = 0;
        for (MapleDisease statup : statups) {
            mask |= statup.getValue();
        }
        return mask;
    }

    /**
     * It is important that statups is in the correct order (see decleration
     * order in MapleBuffStat) since this method doesn't do automagical
     * reordering.
     *
     * @param buffid
     * @param bufflength
     * @param statups
     * @param morph
     * @param firstLong
     * @return
     */
    public static MaplePacket giveBuff(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups, MapleStatEffect effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        // 17 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 07 00 AE E1 3E 00 68 B9 01 00 00 00 00 00
        writeLongMask(mplew, statups);

        for (Pair<MapleBuffStat, Integer> statup : statups) {
            mplew.writeShort(statup.getRight().shortValue());
            mplew.writeInt(buffid);
            mplew.writeInt(bufflength);
        }
        mplew.writeShort(0); // delay,  wk charges have 600 here o.o
        mplew.writeShort(0); // combo 600, too
        mplew.write(effect.isMorph() || effect.isPirateMorph() ? 2 : 0); // Test

        return mplew.getPacket();
    }

    public static MaplePacket giveDash(List<Pair<MapleBuffStat, Integer>> statups, int duration) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        mplew.writeLong(MapleBuffStat.DASH.getValue());
        mplew.writeLong(0);

        mplew.writeShort(0);
        for (Pair<MapleBuffStat, Integer> stat : statups) {
            if (stat.getLeft().getValue() != MapleBuffStat.DASH.getValue()) {
                mplew.writeInt(stat.getRight().shortValue());
                mplew.writeInt(5001005);
                mplew.writeInt(0);
                mplew.writeShort(duration);
                mplew.write(0);
            }
        }
        mplew.writeShort(0);
        mplew.write(2);

        return mplew.getPacket();
    }

    public static MaplePacket giveForeignDash(List<Pair<MapleBuffStat, Integer>> statups, int duration, int cid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);
        mplew.writeLong(0);
        mplew.writeLong(MapleBuffStat.DASH.getValue());
        mplew.writeShort(0);
        for (Pair<MapleBuffStat, Integer> stat : statups) {
            if (stat.getLeft().getValue() != MapleBuffStat.DASH.getValue()) {
                mplew.writeInt(stat.getRight().shortValue());
                mplew.writeInt(5001005);
                mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
                mplew.writeShort(duration);
            }
        }
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    /**
     * @param buffid
     * @param bufflength
     * @param statups
     * @param morph
     * @param firstLong
     * @return
     */
    public static MaplePacket giveSpeedInfusion(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups, int addedInfo) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        long mask = getLongMask(statups);
        mplew.writeLong(mask);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(statups.get(0).getRight().intValue());
        mplew.writeInt(buffid);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeShort(bufflength);
        mplew.writeShort(addedInfo);
        return mplew.getPacket();
    }

    public static MaplePacket giveInfusion(List<Pair<MapleBuffStat, Integer>> statups, int buffid, int bufflength) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        // 17 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 07 00 AE E1 3E 00 68 B9 01 00 00 00 00 00

        writeLongMask(mplew, statups);

        /*	for (Pair<MapleBuffStat, Integer> statup : statups) {
        mplew.writeInt(statup.getRight().shortValue());
        mplew.writeInt(buffid);
        mplew.writeInt(0);
        mplew.writeShort(bufflength);
        mplew.write(0);
        }
        mplew.writeShort(0); // delay,  wk charges have 600 here o.o
        mplew.writeShort(0); // combo 600, too
        mplew.write(2); // Test*/
        mplew.write(HexTool.getByteArrayFromHexString("00 00 FF FF FF FF F1 23 4E 00 00 00 00 00 00 00 00 00 00 00 6E 00 58 02"));

        return mplew.getPacket();
    }

    public static MaplePacket giveForeignInfusion(int cid, int speed, int duration) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);
        mplew.writeLong(0);
        mplew.writeLong(MapleBuffStat.MORPH.getValue()); //transform buffstat
        mplew.writeShort(0);
        mplew.writeInt(speed);
        mplew.writeInt(5121009);
        mplew.writeLong(0);
        mplew.writeInt(duration);
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    /**
     * @param buffid
     * @param bufflength
     * @param statups
     * @param morph
     * @param firstLong
     * @return
     */
    public static MaplePacket givePirateBuff(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        long mask = getLongMask(statups);
        mplew.writeLong(mask);
        mplew.writeLong(0);
        mplew.writeShort(0);
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            mplew.writeShort(statup.getRight().shortValue());
            mplew.writeShort(0);
            mplew.writeInt(buffid);
            mplew.writeInt(0);
            mplew.write(0);
            mplew.writeShort(bufflength);
        }
        mplew.writeShort(0);
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket giveMount(int buffid, int skillid, List<Pair<MapleBuffStat, Integer>> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);

        writeLongMask(mplew, statups);

        mplew.writeShort(0);
        mplew.writeInt(buffid); // 1902000 saddle
        mplew.writeInt(skillid); // skillid
        mplew.writeInt(0); // Server tick value
        mplew.writeShort(0);
        mplew.write(0);
        mplew.write(1); // Total buffed times

        return mplew.getPacket();
    }

    public static MaplePacket giveDebuff(List<Pair<MapleDisease, Integer>> statups, MobSkill skill) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        long mask = getLongMaskD(statups);
        mplew.writeLong(0);
        mplew.writeLong(mask);
        for (Pair<MapleDisease, Integer> statup : statups) {
            mplew.writeShort(statup.getRight().shortValue());
            mplew.writeShort(skill.getSkillId());
            mplew.writeShort(skill.getSkillLevel());
            mplew.writeInt((int) skill.getDuration());
        }
        mplew.writeShort(0); // ??? wk charges have 600 here o.o
        mplew.writeShort(900);//Delay
        mplew.write(1);
        return mplew.getPacket();
    }

    public static MaplePacket giveEnergyChargeTest(int bar) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        long mask = 0;
        mask |= MapleBuffStat.ENERGY_CHARGE.getValue();
        mplew.writeLong(mask);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeShort(bar); // 0 = no bar, 10000 = full bar
        mplew.writeLong(0);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket giveForeignEnergyCharge(
            int cid, int barammount) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);
        mplew.writeLong(0);
        mplew.writeLong(MapleBuffStat.ENERGY_CHARGE.getValue()); //energy charge buffstat
        mplew.writeShort(0);
        mplew.writeShort(barammount); // 0=no bar, 10000=full bar
        mplew.writeShort(0);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeShort(0);
        return mplew.getPacket();
    }

    public static MaplePacket giveForeignDebuff(int cid, List<Pair<MapleDisease, Integer>> statups, MobSkill skill) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);
        long mask = getLongMaskD(statups);
        mplew.writeLong(0);
        mplew.writeLong(mask);
        for (@SuppressWarnings("unused") Pair<MapleDisease, Integer> statup : statups) {
            //mplew.writeShort(statup.getRight().byteValue());
            mplew.writeShort(skill.getSkillId());
            mplew.writeShort(skill.getSkillLevel());
        }
        mplew.writeShort(0); // same as give_buff
        mplew.writeShort(900);//Delay
        return mplew.getPacket();
    }

    public static MaplePacket cancelForeignDebuff(int cid, long mask) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF);
        mplew.writeInt(cid);
        mplew.writeLong(0);
        mplew.writeLong(mask);
        return mplew.getPacket();
    }

    public static MaplePacket showMonsterRiding(int cid, List<Pair<MapleBuffStat, Integer>> statups, int itemId, int skillId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);

        writeLongMask(mplew, statups);

        mplew.writeShort(0);
        mplew.writeInt(itemId);
        mplew.writeInt(skillId);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.write(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket showPirateBuff(int cid, int skillid, int time, List<Pair<MapleBuffStat, Integer>> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);
        long mask = getLongMask(statups);
        mplew.writeLong(mask);
        mplew.writeLong(0);
        mplew.writeShort(0);
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            mplew.writeShort(statup.getRight());
            mplew.writeShort(0);
            mplew.writeInt(skillid);
            mplew.writeInt(0);
            mplew.write(0);
            mplew.writeShort(time);
        }
        mplew.writeShort(0);
        return mplew.getPacket();
    }

    public static MaplePacket showSpeedInfusion(int cid, int skillid, int time, List<Pair<MapleBuffStat, Integer>> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);
        long mask = getLongMask(statups);
        mplew.writeLong(mask);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(statups.get(0).getRight());
        mplew.writeInt(skillid);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeShort(time);
        mplew.writeShort(0);
        return mplew.getPacket();
    }

    public static MaplePacket giveForeignBuff(int cid, List<Pair<MapleBuffStat, Integer>> statups, MapleStatEffect effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF);
        mplew.writeInt(cid);
        writeLongMask(mplew, statups);
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            if (effect.isMorph() && !effect.isPirateMorph()) {
                mplew.write(statup.getRight().byteValue());
            } else {
                mplew.writeShort(statup.getRight().shortValue());
            }
        }
        mplew.writeShort(0); // same as give_buff
        if (effect.isMorph()) {
            mplew.writeShort(0);
        }
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket cancelForeignBuff(int cid, List<MapleBuffStat> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF);
        mplew.writeInt(cid);
        long mask = getLongMaskFromList(statups);
        mplew.writeLong(isFirstLong(statups) ? mask : 0);
        mplew.writeLong(isFirstLong(statups) ? 0 : mask);
        return mplew.getPacket();
    }

    public static MaplePacket cancelBuff(List<MapleBuffStat> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_BUFF);
        long mask = getLongMaskFromList(statups);
        mplew.writeLong(isFirstLong(statups) ? mask : 0);
        mplew.writeLong(isFirstLong(statups) ? 0 : mask);
        mplew.write(3); // wtf?
        return mplew.getPacket();
    }
//MSEA by LaiLai

    public static MaplePacket cancelDebuff(long mask) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_BUFF);
        mplew.writeLong(0);
        mplew.writeLong(mask);
        mplew.write(1);
        return mplew.getPacket();
    }

    private static boolean isFirstLong(List<MapleBuffStat> statups) {
        for (MapleBuffStat stat : statups) {
            if (stat.equals(MapleBuffStat.DASH)
                    || stat.equals(MapleBuffStat.DASH2)
                    || stat.equals(MapleBuffStat.SPEED_INFUSION)
                    || stat.equals(MapleBuffStat.MONSTER_RIDING)
                    || stat.equals(MapleBuffStat.ENERGY_CHARGE)) {
                return true;
            }
        }
        return false;
    }

    public static MaplePacket cancelDebuff(List<MapleDisease> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_BUFF);
        long mask = getLongMaskFromListD(statups);
        mplew.writeLong(0);
        mplew.writeLong(mask);
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket getPlayerShopChat(MapleCharacter c, String chat, boolean owner) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(HexTool.getByteArrayFromHexString("06 08"));
        mplew.write(owner ? 0 : 1);
        mplew.writeMapleAsciiString(c.getName() + " : " + chat);
        return mplew.getPacket();
    }

    public static MaplePacket getPlayerShopNewVisitor(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(HexTool.getByteArrayFromHexString("04 02"));
        PacketHelper.addCharLook(mplew, c, false);
        mplew.writeMapleAsciiString(c.getName());
        return mplew.getPacket();
    }

    public static MaplePacket getTradePartnerAdd(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(HexTool.getByteArrayFromHexString("04 01"));
        PacketHelper.addCharLook(mplew, c, false);
        mplew.writeMapleAsciiString(c.getName());
        return mplew.getPacket();
    }

    public static MaplePacket getTradeInvite(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(HexTool.getByteArrayFromHexString("02 03"));
        mplew.writeMapleAsciiString(c.getName());
        mplew.write(HexTool.getByteArrayFromHexString("B7 50 00 00"));
        return mplew.getPacket();
    }

    public static MaplePacket getTradeMesoSet(byte number, int meso) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(0xF);
        mplew.write(number);
        mplew.writeInt(meso);
        return mplew.getPacket();
    }

    public static MaplePacket getTradeItemAdd(byte number, IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(0xE);
        mplew.write(number);
        PacketHelper.addItemInfo(mplew, item, false, false);
        return mplew.getPacket();
    }

    public static MaplePacket getTradeStart(MapleClient c, MapleTrade trade, byte number) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(HexTool.getByteArrayFromHexString("05 03 02"));
        mplew.write(number);
        if (number == 1) {
            mplew.write(0);
            PacketHelper.addCharLook(mplew, trade.getPartner().getChr(), false);
            mplew.writeMapleAsciiString(trade.getPartner().getChr().getName());
        }
        mplew.write(number);
        /*if (number == 1) {
        mplew.write(0);
        mplew.writeInt(c.getPlayer().getId());
        }*/
        PacketHelper.addCharLook(mplew, c.getPlayer(), false);
        mplew.writeMapleAsciiString(c.getPlayer().getName());
        mplew.write(0xFF);
        return mplew.getPacket();
    }

    public static MaplePacket getTradeConfirmation() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(0x10);
        return mplew.getPacket();
    }

    public static MaplePacket TradeMessage(final byte UserSlot, final byte message) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(0xA);
        mplew.write(UserSlot);
        mplew.write(message);
        //0x06 = success [tax is automated]
        //0x07 = unsuccessful
        //0x08 = "You cannot make the trade because there are some items which you cannot carry more than one."
        //0x09 = "You cannot make the trade because the other person's on a different map."

        return mplew.getPacket();
    }

    public static MaplePacket getTradeCancel(byte number) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(0xA);
        mplew.write(number);
        mplew.write(2);
        return mplew.getPacket();
    }
    /*
    public static MaplePacket updateCharBox(MapleCharacter c) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX);
    mplew.writeInt(c.getId());
    if (c.getPlayerShop() != null) {
    addAnnounceBox(mplew, c.getPlayerShop());
    } else {
    mplew.write(0);
    }
    return mplew.getPacket();
    }
     */

    public static MaplePacket getNPCTalk(int npc, byte msgType, String talk, String endBytes, byte type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.NPC_TALK);
        mplew.write(4);
        mplew.writeInt(npc);
        mplew.write(msgType);
//	mplew.write(type); // 1 = No ESC, 3 = show character + no sec
        mplew.writeMapleAsciiString(talk);
        mplew.write(HexTool.getByteArrayFromHexString(endBytes));

        return mplew.getPacket();
    }

    public static final MaplePacket getMapSelection(final int npcid, final String sel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_TALK);
        mplew.write(4);
        mplew.writeInt(npcid);
        mplew.write(0xD);
        mplew.writeZeroBytes(5);
        mplew.writeMapleAsciiString(sel);

        return mplew.getPacket();
    }

    public static MaplePacket getNPCTalkStyle(int npc, String talk, int styles[]) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.NPC_TALK);
        mplew.write(4); // ?
        mplew.writeInt(npc);
        mplew.write(7);
        mplew.writeMapleAsciiString(talk);
        mplew.write(styles.length);
        for (int i = 0; i < styles.length; i++) {
            mplew.writeInt(styles[i]);
        }
        return mplew.getPacket();
    }

    public static MaplePacket getNPCTalkNum(int npc, String talk, int def, int min, int max) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.NPC_TALK);
        mplew.write(4); // ?
        mplew.writeInt(npc);
        mplew.write(3);
        mplew.writeMapleAsciiString(talk);
        mplew.writeInt(def);
        mplew.writeInt(min);
        mplew.writeInt(max);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    public static MaplePacket getNPCTalkText(int npc, String talk) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.NPC_TALK);
        mplew.write(4); // ?
        mplew.writeInt(npc);
        mplew.write(2);
        mplew.writeMapleAsciiString(talk);
        mplew.writeInt(0);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    public static MaplePacket showLevelup(int cid) {
        return showForeignEffect(cid, 0);
    }

    public static MaplePacket showJobChange(int cid) {
        return showForeignEffect(cid, 8);
    }

    public static MaplePacket showForeignEffect(int cid, int effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT);
        mplew.writeInt(cid); // ?
        mplew.write(effect);
        return mplew.getPacket();
    }

    public static MaplePacket showBuffeffect(int cid, int skillid, int effectid) {
        return showBuffeffect(cid, skillid, effectid, (byte) 3, false);
    }

    public static MaplePacket showBuffeffect(int cid, int skillid, int effectid, byte direction, boolean morph) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT);
        mplew.writeInt(cid);
        if (morph) {
            mplew.write(1);
            mplew.writeInt(skillid);
            mplew.write(direction);
        }
        mplew.write(effectid);
        mplew.writeInt(skillid);
        mplew.write(1); // probably buff level but we don't know it and it
        // doesn't really matter
        if (direction != (byte) 3) {
            mplew.write(direction);
        }

        return mplew.getPacket();
    }

    public static MaplePacket showSpecialEffect(int effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
        mplew.write(effect);

        return mplew.getPacket();
    }

    public static MaplePacket showSpecialEffect(int cid, int effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT);
        mplew.writeInt(cid);
        mplew.write(effect);

        return mplew.getPacket();
    }

    public static MaplePacket showOwnBuffEffect(int skillid, int effectid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
        mplew.write(effectid);
        mplew.writeInt(skillid);
        mplew.write(1); // probably buff level but we don't know it and it
        // doesn't really matter
        return mplew.getPacket();
    }

    public static MaplePacket updateSkill(int skillid, int level, int masterlevel) {
        // 1E 00 01 01 00 E9 03 00 00 01 00 00 00 00 00 00 00 01
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_SKILLS);
        mplew.write(1);
        mplew.writeShort(1);
        mplew.writeInt(skillid);
        mplew.writeInt(level);
        mplew.writeInt(masterlevel);
        mplew.write(1);
        return mplew.getPacket();
    }

    public static MaplePacket updateQuestMobKills(MapleQuestStatus status) {
        // 21 00 01 FB 03 01 03 00 30 30 31
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(1);
        mplew.writeShort(status.getQuest().getId());
        mplew.write(1);
        String killStr = "";
        for (int kills : status.getMobKills().values()) {
            killStr += StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3);
        }
        mplew.writeMapleAsciiString(killStr);
        mplew.writeInt(0);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    public static MaplePacket getShowQuestCompletion(int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_QUEST_COMPLETION);
        mplew.writeShort(id);
        return mplew.getPacket();
    }

    public static MaplePacket getKeymap(MapleKeyLayout layout) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.KEYMAP);
        mplew.write(0);
        layout.writeData(mplew);
        return mplew.getPacket();
    }

    public static MaplePacket getWhisper(String sender, int channel, String text) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(0x12);
        mplew.writeMapleAsciiString(sender);
        mplew.writeShort(channel - 1); // I guess this is the channel
        mplew.writeMapleAsciiString(text);
        return mplew.getPacket();
    }

    /**
     *
     * @param target name of the target character
     * @param reply error code: 0x0 = cannot find char, 0x1 = success
     * @return the MaplePacket
     */
    public static MaplePacket getWhisperReply(String target, byte reply) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(0x0A); // whisper?

        mplew.writeMapleAsciiString(target);
        mplew.write(reply);
        // System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
        return mplew.getPacket();
    }

    public static MaplePacket getFindReplyWithMap(String target, int mapid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(9);
        mplew.writeMapleAsciiString(target);
        mplew.write(1);
        mplew.writeInt(mapid);
        // ?? official doesn't send zeros here but whatever
        mplew.write(new byte[8]);
        return mplew.getPacket();
    }

    public static MaplePacket getFindReply(String target, int channel) {
        // Received UNKNOWN (1205941596.79689): (25)
        // 54 00 09 07 00 64 61 76 74 73 61 69 01 86 7F 3D 36 D5 02 00 00 22 00
        // 00 00
        // T....davtsai..=6...."...
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(9);
        mplew.writeMapleAsciiString(target);
        mplew.write(3);
        mplew.writeInt(channel - 1);
        return mplew.getPacket();
    }

    public static MaplePacket getInventoryFull() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(1);
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket getShowInventoryFull() {
        return getShowInventoryStatus(0xff);
    }

    public static MaplePacket showItemUnavailable() {
        return getShowInventoryStatus(0xfe);
    }

    public static MaplePacket getShowInventoryStatus(int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(0);
        mplew.write(mode);
        mplew.writeInt(0);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    public static MaplePacket getStorage(int npcId, byte slots, Collection<IItem> items, int meso) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE);
        mplew.write(0x16);
        mplew.writeInt(npcId);
        mplew.write(slots);
        mplew.writeShort(0x7E);
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.writeInt(meso);
        mplew.writeShort(0);
        mplew.write((byte) items.size());
        for (IItem item : items) {
            PacketHelper.addItemInfo(mplew, item, true, true);
        }
        mplew.writeShort(0);
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket getStorageFull() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE);
        mplew.write(0x11);
        return mplew.getPacket();
    }

    public static MaplePacket mesoStorage(byte slots, int meso) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE);
        mplew.write(0x13);
        mplew.write(slots);
        mplew.writeShort(2);
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.writeInt(meso);
        return mplew.getPacket();
    }

    public static MaplePacket storeStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE);
        mplew.write(0xD);
        mplew.write(slots);
        mplew.writeShort(type.getBitfieldEncoding());
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.write(items.size());
        for (IItem item : items) {
            PacketHelper.addItemInfo(mplew, item, true, true);
        }
        return mplew.getPacket();
    }

    public static MaplePacket takeOutStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE);
        mplew.write(0x9);
        mplew.write(slots);
        mplew.writeShort(type.getBitfieldEncoding());
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.write(items.size());
        for (IItem item : items) {
            PacketHelper.addItemInfo(mplew, item, true, true);
        }
        return mplew.getPacket();
    }

    public static MaplePacket giveFameResponse(int mode, String charname, int newfame) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FAME_RESPONSE);
        mplew.write(0);
        mplew.writeMapleAsciiString(charname);
        mplew.write(mode);
        mplew.writeShort(newfame);
        mplew.writeShort(0);
        return mplew.getPacket();
    }

    /**
     * status can be: <br>
     * 0: ok, use giveFameResponse<br>
     * 1: the username is incorrectly entered<br>
     * 2: users under level 15 are unable to toggle with fame.<br>
     * 3: can't raise or drop fame anymore today.<br>
     * 4: can't raise or drop fame for this character for this month anymore.<br>
     * 5: received fame, use receiveFame()<br>
     * 6: level of fame neither has been raised nor dropped due to an unexpected
     * error
     *
     * @param status
     * @param mode
     * @param charname
     * @param newfame
     * @return
     */
    public static MaplePacket giveFameErrorResponse(int status) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FAME_RESPONSE);
        mplew.write(status);
        return mplew.getPacket();
    }

    public static MaplePacket receiveFame(int mode, String charnameFrom) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FAME_RESPONSE);
        mplew.write(5);
        mplew.writeMapleAsciiString(charnameFrom);
        mplew.write(mode);
        return mplew.getPacket();
    }

    public static MaplePacket partyCreated() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION);
        mplew.write(8);
        mplew.writeShort(0x8b);
        mplew.writeShort(2);
        mplew.write(CHAR_INFO_MAGIC);
        mplew.write(CHAR_INFO_MAGIC);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    public static MaplePacket partyInvite(MapleCharacter from) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION);
        mplew.write(4);
        mplew.writeInt(from.getParty().getId());
        mplew.writeMapleAsciiString(from.getName());
        mplew.write(0);
        return mplew.getPacket();
    }

    /**
     * 10: a beginner can't create a party<br>
     * 11/14/19: your request for a party didn't work due to an unexpected error<br>
     * 13: you have yet to join a party<br>
     * 16: already have joined a party<br>
     * 17: the party you are trying to join is already at full capacity<br>
     * 18: unable to find the requested character in this channel<br>
     *
     * @param message
     * @return
     */
    public static MaplePacket partyStatusMessage(int message) {
        // 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION);
        mplew.write(message);
        return mplew.getPacket();
    }

    /**
     * 22: has denied the invitation<br>
     *
     * @param message
     * @param charname
     * @return
     */
    public static MaplePacket partyStatusMessage(int message, String charname) {
        // 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION);
        mplew.write(message);
        mplew.writeMapleAsciiString(charname);
        return mplew.getPacket();
    }

    private static void addPartyStatus(int forchannel, MapleParty party, LittleEndianWriter lew, boolean leaving) {
        List<MaplePartyCharacter> partymembers = new ArrayList<MaplePartyCharacter>(party.getMembers());
        while (partymembers.size() < 6) {
            partymembers.add(new MaplePartyCharacter());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeInt(partychar.getId());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeAsciiString(StringUtil.getRightPaddedStr(partychar.getName(), '\0', 13));
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeInt(partychar.getJobId());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeInt(partychar.getLevel());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            if (partychar.isOnline()) {
                lew.writeInt(partychar.getChannel() - 1);
            } else {
                lew.writeInt(-2);
            }
        }
        lew.writeInt(party.getLeader().getId());
        for (MaplePartyCharacter partychar : partymembers) {
            if (partychar.getChannel() == forchannel) {
                lew.writeInt(partychar.getMapid());
            } else {
                lew.writeInt(0);
            }
        }
        for (MaplePartyCharacter partychar : partymembers) {
            if (partychar.getChannel() == forchannel && !leaving) {
                lew.writeInt(partychar.getDoorTown());
                lew.writeInt(partychar.getDoorTarget());
                lew.writeInt(partychar.getDoorPosition().x);
                lew.writeInt(partychar.getDoorPosition().y);
            } else {
                lew.writeInt(0);
                lew.writeInt(0);
                lew.writeInt(0);
                lew.writeInt(0);
            }
        }
    }

    public static MaplePacket updateParty(int forChannel, MapleParty party, PartyOperation op, MaplePartyCharacter target) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION);
        switch (op) {
            case DISBAND:
            case EXPEL:
            case LEAVE:
                mplew.write(0xC);
                mplew.writeInt(40546);
                mplew.writeInt(target.getId());
                if (op == PartyOperation.DISBAND) {
                    mplew.write(0);
                    mplew.writeInt(party.getId());
                } else {
                    mplew.write(1);
                    if (op == PartyOperation.EXPEL) {
                        mplew.write(1);
                    } else {
                        mplew.write(0);
                    }
                    mplew.writeMapleAsciiString(target.getName());
                    addPartyStatus(forChannel, party, mplew, false);
                    // addLeavePartyTail(mplew);
                }
                break;
            case JOIN:
                mplew.write(0xF);
                mplew.writeInt(40546);
                mplew.writeMapleAsciiString(target.getName());
                addPartyStatus(forChannel, party, mplew, false);
                // addJoinPartyTail(mplew);
                break;
            case SILENT_UPDATE:
            case LOG_ONOFF:
                mplew.write(0x7);
                mplew.writeInt(party.getId());
                addPartyStatus(forChannel, party, mplew, false);
                break;
        }
        return mplew.getPacket();
    }

    public static MaplePacket partyPortal(int townId, int targetId, Point position) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION);
        mplew.writeShort(0x23);
        mplew.writeInt(townId);
        mplew.writeInt(targetId);
        mplew.writeShort(position.x);
        mplew.writeShort(position.y);
        return mplew.getPacket();
    }

    public static MaplePacket updatePartyMemberHP(int cid, int curhp, int maxhp) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_PARTYMEMBER_HP);
        mplew.writeInt(cid);
        mplew.writeInt(curhp);
        mplew.writeInt(maxhp);
        return mplew.getPacket();
    }

    /**
     * mode: 0 buddychat; 1 partychat; 2 guildchat
     *
     * @param name
     * @param chattext
     * @param mode
     * @return
     */
    public static MaplePacket multiChat(String name, String chattext, int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MULTICHAT);
        mplew.write(mode);
        mplew.writeMapleAsciiString(name);
        mplew.writeMapleAsciiString(chattext);
        return mplew.getPacket();
    }

    public static MaplePacket getClock(int time) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CLOCK);
        mplew.write(2);
        mplew.writeInt(time);
        return mplew.getPacket();
    }

    public static MaplePacket getClockTime(int hour, int min, int sec) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CLOCK);
        mplew.write(1);
        mplew.write(hour);
        mplew.write(min);
        mplew.write(sec);
        return mplew.getPacket();
    }

    public static MaplePacket spawnMist(MapleMist mist) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SPAWN_MIST);
        mplew.writeInt(mist.getObjectId());
        mplew.writeInt(mist.isMobMist() ? 0 : mist.isPoisonMist() ? 1 : 2);
        if (mist.getOwner() != null) {
            mplew.writeInt(mist.getOwner().getId());
            mplew.writeInt(mist.getSourceSkill().getId());
            mplew.write(mist.getSkillLevel());
        } else {
            mplew.writeInt(mist.getMobOwner().getId());
            mplew.writeInt(mist.getMobSkill().getSkillId());
            mplew.write(mist.getMobSkill().getSkillLevel());
        }
        mplew.writeShort(mist.getSkillDelay());
        mplew.writeInt(mist.getBox().x);
        mplew.writeInt(mist.getBox().y);
        mplew.writeInt(mist.getBox().x + mist.getBox().width);
        mplew.writeInt(mist.getBox().y + mist.getBox().height);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket removeMist(int oid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.REMOVE_MIST);
        mplew.writeInt(oid);
        return mplew.getPacket();
    }

    public static MaplePacket damageSummon(int cid, int summonSkillId, int damage, int unkByte, int monsterIdFrom) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DAMAGE_SUMMON);
        mplew.writeInt(cid);
        mplew.writeInt(summonSkillId);
        mplew.write(unkByte);
        mplew.writeInt(damage);
        mplew.writeInt(monsterIdFrom);
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket buddylistMessage(byte message) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BUDDYLIST);
        mplew.write(message);
        return mplew.getPacket();
    }

    public static MaplePacket updateBuddylist(Collection<BuddylistEntry> buddylist) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BUDDYLIST);
        mplew.write(7);
        mplew.write(buddylist.size());
        for (BuddylistEntry buddy : buddylist) {
            if (buddy.isVisible()) {
                mplew.writeInt(buddy.getCharacterId());
                mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getName(), '\0', 13));
                mplew.write(0);
                mplew.writeInt(buddy.getChannel() - 1);
                mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getGroup(), '\0', 17));
            }
        }
        for (int x = 0; x < buddylist.size(); x++) {
            mplew.writeInt(0);
        }
        return mplew.getPacket();
    }

    public static MaplePacket requestBuddylistAdd(int cidFrom, String nameFrom) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BUDDYLIST);
        mplew.write(9);
        mplew.writeInt(cidFrom);
        mplew.writeMapleAsciiString(nameFrom);
        mplew.writeInt(cidFrom);
        mplew.writeAsciiString(StringUtil.getRightPaddedStr(nameFrom, '\0', 13));
        mplew.write(1);
        mplew.write(31);
        mplew.writeShort(0);
        mplew.write(0);
        mplew.writeAsciiString(StringUtil.getRightPaddedStr("Default Group", '\0', 17));
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket updateBuddyChannel(int characterid, int channel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BUDDYLIST);
        // 2B 00 14 30 C0 23 00 00 11 00 00 00
        mplew.write(0x14);
        mplew.writeInt(characterid);
        mplew.write(0);
        mplew.writeInt(channel);
        // 2B 00 14 30 C0 23 00 00 0D 00 00 00
        // 2B 00 14 30 75 00 00 00 11 00 00 00
        return mplew.getPacket();
    }

    public static MaplePacket itemEffect(int characterid, int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_EFFECT);
        mplew.writeInt(characterid);
        mplew.writeInt(itemid);
        return mplew.getPacket();
    }

    public static MaplePacket updateBuddyCapacity(int capacity) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BUDDYLIST);
        mplew.write(0x15);
        mplew.write(capacity);
        return mplew.getPacket();
    }

    public static MaplePacket showChair(int characterid, int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_CHAIR);
        mplew.writeInt(characterid);
        mplew.writeInt(itemid);
        return mplew.getPacket();
    }

    public static MaplePacket cancelChair() {
        return cancelChair(-1);
    }

    public static MaplePacket cancelChair(int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_CHAIR);
        if (id == -1) {
            mplew.write(0);
        } else {
            mplew.write(1);
            mplew.writeShort(id);
        }
        return mplew.getPacket();
    }

    // is there a way to spawn reactors non-animated?
    public static MaplePacket spawnReactor(MapleReactor reactor) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        Point pos = reactor.getPosition();
        mplew.writeShort(SendPacketOpcode.REACTOR_SPAWN);
        mplew.writeInt(reactor.getObjectId());
        mplew.writeInt(reactor.getId());
        mplew.write(reactor.getState());
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket triggerReactor(MapleReactor reactor, int stance) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        Point pos = reactor.getPosition();
        mplew.writeShort(SendPacketOpcode.REACTOR_HIT);
        mplew.writeInt(reactor.getObjectId());
        mplew.write(reactor.getState());
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);
        mplew.writeShort(stance);
        mplew.write(0);
        //frame delay, set to 5 since there doesn't appear to be a fixed formula for it
        mplew.write(5);
        return mplew.getPacket();
    }

    public static MaplePacket destroyReactor(MapleReactor reactor) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        Point pos = reactor.getPosition();
        mplew.writeShort(SendPacketOpcode.REACTOR_DESTROY);
        mplew.writeInt(reactor.getObjectId());
        mplew.write(reactor.getState());
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);
        return mplew.getPacket();
    }

    public static MaplePacket musicChange(String song) {
        return environmentChange(song, 6);
    }

    public static MaplePacket showEffect(String effect) {
        return environmentChange(effect, 3);
    }

    public static MaplePacket playSound(String sound) {
        return environmentChange(sound, 4);
    }

    public static MaplePacket environmentChange(String env, int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BOSS_ENV);
        mplew.write(mode);
        mplew.writeMapleAsciiString(env);
        return mplew.getPacket();
    }

    public static MaplePacket startMapEffect(String msg, int itemid, boolean active) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MAP_EFFECT);
        mplew.write(active ? 0 : 1);
        mplew.writeInt(itemid);
        if (active) {
            mplew.writeMapleAsciiString(msg);
        }
        return mplew.getPacket();
    }

    public static MaplePacket removeMapEffect() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MAP_EFFECT);
        mplew.write(0);
        mplew.writeInt(0);
        return mplew.getPacket();
    }

    public static void addThread(MaplePacketLittleEndianWriter mplew, ResultSet rs) throws SQLException {
        mplew.writeInt(rs.getInt("localthreadid"));
        mplew.writeInt(rs.getInt("postercid"));
        mplew.writeMapleAsciiString(rs.getString("name"));
        mplew.writeLong(PacketHelper.getKoreanTimestamp(rs.getLong("timestamp")));
        mplew.writeInt(rs.getInt("icon"));
        mplew.writeInt(rs.getInt("replycount"));
    }

    public static MaplePacket BBSThreadList(ResultSet rs, int start) throws SQLException {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BBS_OPERATION);
        mplew.write(0x06);
        if (!rs.last()) //no result at all
        {
            mplew.write(0);
            mplew.writeInt(0);
            mplew.writeInt(0);
            return mplew.getPacket();
        }
        int threadCount = rs.getRow();
        if (rs.getInt("localthreadid") == 0) //has a notice
        {
            mplew.write(1);
            addThread(mplew, rs);
            threadCount--; //one thread didn't count (because it's a notice)

        } else {
            mplew.write(0);
        }
        if (!rs.absolute(start + 1)) //seek to the thread before where we start
        {
            rs.first(); //uh, we're trying to start at a place past possible
            start = 0;
            // System.out.println("Attempting to start past threadCount");
        }
        mplew.writeInt(threadCount);
        mplew.writeInt(Math.min(10, threadCount - start));
        for (int i = 0; i < Math.min(10, threadCount - start); i++) {
            addThread(mplew, rs);
            rs.next();
        }
        return mplew.getPacket();
    }

    public static MaplePacket showThread(int localthreadid, ResultSet threadRS, ResultSet repliesRS)
            throws SQLException, RuntimeException {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BBS_OPERATION);
        mplew.write(0x07);
        mplew.writeInt(localthreadid);
        mplew.writeInt(threadRS.getInt("postercid"));
        mplew.writeLong(PacketHelper.getKoreanTimestamp(threadRS.getLong("timestamp")));
        mplew.writeMapleAsciiString(threadRS.getString("name"));
        mplew.writeMapleAsciiString(threadRS.getString("startpost"));
        mplew.writeInt(threadRS.getInt("icon"));
        if (repliesRS != null) {
            int replyCount = threadRS.getInt("replycount");
            mplew.writeInt(replyCount);
            int i;
            for (i = 0; i
                    < replyCount && repliesRS.next(); i++) {
                mplew.writeInt(repliesRS.getInt("replyid"));
                mplew.writeInt(repliesRS.getInt("postercid"));
                mplew.writeLong(PacketHelper.getKoreanTimestamp(repliesRS.getLong("timestamp")));
                mplew.writeMapleAsciiString(repliesRS.getString("content"));
            }
            if (i != replyCount || repliesRS.next()) {
                //in the unlikely event that we lost count of replyid
                throw new RuntimeException(String.valueOf(threadRS.getInt("threadid")));
                //we need to fix the database and stop the packet sending
                //or else it'll probably error 38 whoever tries to read it
                //there is ONE case not checked, and that's when the thread
                //has a replycount of 0 and there is one or more replies to the
                //thread in bbs_replies
            }
        } else {
            mplew.writeInt(0); //0 replies
        }
        return mplew.getPacket();
    }

    public static MaplePacket skillEffect(MapleCharacter from, int skillId, int level, byte flags, int speed) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SKILL_EFFECT);
        mplew.writeInt(from.getId());
        mplew.writeInt(skillId);
        mplew.write(level);
        mplew.write(flags);
        mplew.write(speed);
        return mplew.getPacket();
    }

    public static MaplePacket skillCancel(MapleCharacter from, int skillId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_SKILL_EFFECT);
        mplew.writeInt(from.getId());
        mplew.writeInt(skillId);
        return mplew.getPacket();
    }

    public static MaplePacket showMagnet(int mobid, byte success) {  // Monster Magnet
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_MAGNET);
        mplew.writeInt(mobid);
        mplew.write(success);
        return mplew.getPacket();
    }

    /**
     * Sends a Player Hint, something that pops up above your character!
     *
     * @return The packet.
     */
    public static MaplePacket sendHint(String hint) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
        mplew.writeShort(SendPacketOpcode.PLAYER_HINT);
        mplew.writeMapleAsciiString(hint);
        mplew.write(HexTool.getByteArrayFromHexString("FA 00 05 00 01"));
        return mplew.getPacket();
    }

    /**
     * Sends a player hint.
     *
     * @param hint The hint it's going to send.
     * @param width How tall the box is going to be.
     * @param height How long the box is going to be.
     * @return The player hint packet.
     */
    public static MaplePacket sendHint(String hint, int width, int height) {
        if (width < 1) {
            width = hint.length() * 10;
            if (width < 40) {
                width = 40;
            }
        }
        if (height < 5) {
            height = 5;
        }
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_HINT);
        mplew.writeMapleAsciiString(hint);
        mplew.writeShort(width);
        mplew.writeShort(height);
        mplew.write(1);
        return mplew.getPacket();
    }

    public static MaplePacket messengerInvite(String from, int messengerid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESSENGER);
        mplew.write(0x03);
        mplew.writeMapleAsciiString(from);
        mplew.write(0x00);
        mplew.writeInt(messengerid);
        mplew.write(0x00);
        return mplew.getPacket();
    }

    public static MaplePacket addMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESSENGER);
        mplew.write(0x00);
        mplew.write(position);
        PacketHelper.addCharLook(mplew, chr, true);
        mplew.writeMapleAsciiString(from);
        mplew.write(channel);
        mplew.write(0x00);
        return mplew.getPacket();
    }

    public static MaplePacket removeMessengerPlayer(int position) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESSENGER);
        mplew.write(0x02);
        mplew.write(position);
        return mplew.getPacket();
    }

    public static MaplePacket updateMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESSENGER);
        mplew.write(0x07);
        mplew.write(position);
        PacketHelper.addCharLook(mplew, chr, true);
        mplew.writeMapleAsciiString(from);
        mplew.write(channel);
        mplew.write(0x00);
        return mplew.getPacket();
    }

    public static MaplePacket joinMessenger(int position) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESSENGER);
        mplew.write(0x01);
        mplew.write(position);
        return mplew.getPacket();
    }

    public static MaplePacket messengerChat(String text) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESSENGER);
        mplew.write(0x06);
        mplew.writeMapleAsciiString(text);
        return mplew.getPacket();
    }

    public static MaplePacket messengerNote(String text, int mode, int mode2) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESSENGER);
        mplew.write(mode);
        mplew.writeMapleAsciiString(text);
        mplew.write(mode2);
        return mplew.getPacket();
    }

    public static MaplePacket getFindReplyWithCS(String target) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(9);
        mplew.writeMapleAsciiString(target);
        mplew.write(2);
        mplew.writeInt(-1);
        return mplew.getPacket();
    }

    public static MaplePacket skillCooldown(int sid, int time) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.COOLDOWN);
        mplew.writeInt(sid);
        mplew.writeShort(time);
        return mplew.getPacket();
    }

    public static MaplePacket skillBookSuccess(MapleCharacter chr, int skillid, int maxlevel, boolean canuse, boolean success) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.USE_SKILL_BOOK);
        mplew.writeInt(chr.getId()); // character id
        mplew.write(1);
        mplew.writeInt(skillid);
        mplew.writeInt(maxlevel);
        mplew.write(canuse ? 1 : 0);
        mplew.write(success ? 1 : 0);
        return mplew.getPacket();
    }

    public static MaplePacket getMacros(SkillMacro[] macros) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SKILL_MACRO);
        int count = 0;
        for (int i = 0; i < 5; i++) {
            if (macros[i] != null) {
                count++;
            }
        }
        mplew.write(count); // number of macros
        for (int i = 0; i < 5; i++) {
            SkillMacro macro = macros[i];
            if (macro != null) {
                mplew.writeMapleAsciiString(macro.getName());
                mplew.write(macro.getShout());
                mplew.writeInt(macro.getSkill1());
                mplew.writeInt(macro.getSkill2());
                mplew.writeInt(macro.getSkill3());
            }
        }
        return mplew.getPacket();
    }

    public static void sendUnkwnNote(String to, String msg, String from) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("INSERT INTO notes (`to`, `from`, `message`, `timestamp`) VALUES (?, ?, ?, ?)");
        ps.setString(1, to);
        ps.setString(2, from);
        ps.setString(3, msg);
        ps.setLong(4, System.currentTimeMillis());
        ps.executeUpdate();
        ps.close();
    }

    /**
     * Gets a gm effect packet (ie. hide, banned, etc.)
     *
     * Possible values for <code>type</code>:<br>
     * 4: You have successfully blocked access.<br>
     * 5: The unblocking has been successful.<br>
     * 6 with Mode 0: You have successfully removed the name from the ranks.<br>
     * 6 with Mode 1: You have entered an invalid character name.<br>
     * 16: GM Hide, mode determines whether or not it is on.<br>
     * 26: Enables minimap<br>
     * 27: Disables minimap<br>
     * 29 with Mode 0: Unable to send the message. Please enter the user's name before warning.<br>
     * 29 with Mode 1: Your warning has been successfully sent.<br>
     *
     * @param type The type
     * @param mode The mode
     * @return The gm effect packet
     */
    public static MaplePacket sendGMOperation(int type, int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GM_OPERATION);
        mplew.write(type);
        mplew.write(mode);
        return mplew.getPacket();
    }

    public static MaplePacket updateHammerItem(IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(0);
        mplew.write(2);
        mplew.write(3);
        mplew.write(item.getType());
        mplew.writeShort(item.getPosition());
        mplew.write(0);
        mplew.write(1);
        PacketHelper.addItemInfo(mplew, item, false, false);
        return mplew.getPacket();
    }

    public static MaplePacket sendYellowTip(String s) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.YELLOW_TIP);
        mplew.write(255);
        mplew.writeMapleAsciiString(s);
        return mplew.getPacket();
    }

    public static MaplePacket dcPacket() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BUDDYLIST);
        mplew.write(7);
        mplew.write(1);
        mplew.writeInt(1337);
        mplew.writeAsciiString("sucker");
        return mplew.getPacket();
    }

    public static MaplePacket spawnNPCRequestController(MapleNPC life, boolean MiniMap) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_NPC_REQUEST_CONTROLLER);
        mplew.write(1);
        mplew.writeInt(life.getObjectId());
        mplew.writeInt(life.getId());
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getCy());
        mplew.write(life.getF() == 1 ? 0 : 1);
        mplew.writeShort(life.getFh());
        mplew.writeShort(life.getRx0());
        mplew.writeShort(life.getRx1());
        mplew.write(MiniMap ? 1 : 0);
        return mplew.getPacket();
    }

    public static MaplePacket spawnPlayerNPC(MapleNPCStats npc, int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_NPC);
        mplew.write(0x01);
        mplew.writeInt(id);
        mplew.writeMapleAsciiString(npc.getName());
        mplew.write(0);
        mplew.write(npc.getSkin());
        mplew.writeInt(npc.getFace());
        mplew.write(0);
        mplew.writeInt(npc.getHair());
        Map<Byte, Integer> equip = npc.getEquips();
        Map<Byte, Integer> myEquip = new LinkedHashMap<Byte, Integer>();
        Map<Byte, Integer> maskedEquip = new LinkedHashMap<Byte, Integer>();
        for (byte position : equip.keySet()) {
            byte pos = (byte) (position * -1);
            if (pos < 100 && myEquip.get(pos) == null) {
                myEquip.put(pos, equip.get(position));
            } else if ((pos > 100 || pos == -128) && pos != 111) { // don't ask. o.o
                pos -= 100;
                if (myEquip.get(pos) != null) {
                    maskedEquip.put(pos, myEquip.get(pos));
                }
                myEquip.put(pos, equip.get(position));
            } else if (myEquip.get(pos) != null) {
                maskedEquip.put(pos, equip.get(position));
            }
        }
        for (Entry<Byte, Integer> entry : myEquip.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }
        mplew.write(0xFF);
        for (Entry<Byte, Integer> entry : maskedEquip.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }
        mplew.write(0xFF);
        Integer cWeapon = equip.get((byte) -111);
        if (cWeapon != null) {
            mplew.writeInt(cWeapon);
        } else {
            mplew.writeInt(0);
        }
        for (int i = 0; i < 3; i++) {
            mplew.writeInt(0);
        }
        return mplew.getPacket();
    }

    public static MaplePacket removeNPC(int npcid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.REMOVE_NPC);
        mplew.writeInt(npcid);
        return mplew.getPacket();
    }

    public static MaplePacket spouseChat(String from, String message, int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SPOUSE_CHAT);
        mplew.write(type);
        if (type == 4) {
            mplew.write(1);
        } else {
            mplew.writeMapleAsciiString(from);
            mplew.write(5);
        }
        mplew.writeMapleAsciiString(message);
        return mplew.getPacket();
    }

    public static MaplePacket showOXQuiz(int questionSet, int questionId, boolean askQuestion) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.OX_QUIZ);
        mplew.write(askQuestion ? 1 : 0);
        mplew.write(questionSet);
        mplew.writeShort(questionId);
        return mplew.getPacket();
    }

    public static MaplePacket getFindBuddyReplyNotInChannel(String stringah, int channel) {  // CREDITS OLIVER. NOT TO BE RELEASED TO PUBLIC
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(0x48);
        mplew.writeMapleAsciiString(stringah);
        mplew.write(3);
        mplew.writeInt(channel - 1);
        return mplew.getPacket();
    }

    public static MaplePacket getFindBuddyReplyInChannel(String stringah, int mapid) { // CREDITS OLIVER. NOT TO BE RELEASED TO PUBLIC
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(0x48);
        mplew.writeMapleAsciiString(stringah);
        mplew.write(1);
        mplew.writeInt(mapid);
        mplew.write(new byte[8]); // ?? official doesn't send zeros here but whatever
        return mplew.getPacket();
    }

    public static MaplePacket getFindBuddyReplyInCS(String stringah) { // CREDITS OLIVER. NOT TO BE RELEASED TO PUBLIC
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(0x48);
        mplew.writeMapleAsciiString(stringah);
        mplew.write(2);
        mplew.writeInt(-1);
        return mplew.getPacket();
    }

    public static MaplePacket getFindReplyWithCSorMTS(String target, boolean mts) { // Oliver
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER);
        mplew.write(9);
        mplew.writeMapleAsciiString(target);
        mplew.write(mts ? 0 : 2);
        mplew.writeInt(-1);
        return mplew.getPacket();
    }

    public static final MaplePacket trembleEffect(final int type, final int delay) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.BOSS_ENV);
        mplew.write(1);
        mplew.write(type);
        mplew.writeInt(delay);
        return mplew.getPacket();
    }

    public static final MaplePacket MapNameDisplay(final int mapid) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOSS_ENV);
        mplew.write(0x3);
        mplew.writeMapleAsciiString("maplemap/enter/" + mapid);

        return mplew.getPacket();
    }

    public static MaplePacket getEnergy(int level) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.ENERGY);
        mplew.writeMapleAsciiString("energy");
        mplew.writeMapleAsciiString(Integer.toString(level));
        return mplew.getPacket();
    }

    public static MaplePacket updateItemInSlot(IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM);
        mplew.write(0); // could be from drop
        mplew.write(2); // always 2
        mplew.write(3); // quantity > 0 (?)
        mplew.write(item.getType()); // inventory type
        mplew.write(item.getPosition()); // item slot
        mplew.writeShort(0);
        mplew.write(1);
        mplew.write(item.getPosition()); // wtf repeat
        PacketHelper.addItemInfo(mplew, item, false, false);
        return mplew.getPacket();

    }

    public static MaplePacket showCygnusIntro(int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        // For globalMS v.74, Credits to Traitor
        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
        mplew.write(0x12);
        mplew.writeMapleAsciiString("Effect/Direction.img/cygnus/Scene" + id);
        return mplew.getPacket();
    }

    public static MaplePacket CygnusIntroLock(boolean enable) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CYGNUS_INTRO_LOCK);
        mplew.write(enable ? 1 : 0);
        return mplew.getPacket();
    }

    public static MaplePacket CygnusIntroDisableUI(boolean enable) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CYGNUS_INTRO_DISABLE_UI);
        mplew.write(enable ? 1 : 0);
        return mplew.getPacket();
    }

    public static MaplePacket showEquipEffect() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_EQUIP_EFFECT);
        return mplew.getPacket();
    }

    public static MaplePacket enableReport() { // by snow
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(0x2C);
        mplew.write(1);
        return mplew.getPacket();
    }

    public static MaplePacket removeCharBox(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX);
        mplew.writeInt(c.getId());
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket getTradeChat(MapleCharacter c, String chat, boolean owner) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION);
        mplew.write(HexTool.getByteArrayFromHexString("06 08"));
        mplew.write(owner ? 0 : 1);
        mplew.writeMapleAsciiString(c.getName() + " : " + chat);
        return mplew.getPacket();
    }

    public static MaplePacket removeItemFromDuey(boolean remove, int Package) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DUEY);
        mplew.write(0x17);
        mplew.writeInt(Package);
        mplew.write(remove ? 3 : 4);
        return mplew.getPacket();
    }

    public static MaplePacket sendDueyMSG(byte operation) {
        return sendDuey(operation, null);
    }

    public static MaplePacket sendDuey(byte operation, List<MapleDueyActions> packages) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DUEY);
        mplew.write(operation);
        if (operation == 8) {
            mplew.write(0);
            mplew.write(packages.size());
            for (MapleDueyActions dp : packages) {
                mplew.writeInt(dp.getPackageId());
                mplew.writeAsciiString(dp.getSender());
                for (int i = dp.getSender().length(); i < 13; i++) {
                    mplew.write(0);
                }
                mplew.writeInt(dp.getMesos());
                mplew.writeLong(KoreanDateUtil.getFileTimestamp(dp.getSentTime(), false));
                mplew.writeLong(0); // Contains message o____o.
                for (int i = 0; i < 48; i++) {
                    mplew.writeInt(Randomizer.nextInt(Integer.MAX_VALUE));
                }
                mplew.writeInt(0);
                mplew.write(0);
                if (dp.getItem() != null) {
                    mplew.write(1);
                    PacketHelper.addItemInfo(mplew, dp.getItem(), true, true);
                } else {
                    mplew.write(0);
                }
            }
            mplew.write(0);

        }
        return mplew.getPacket();
    }

    public static MaplePacket sendAutoHpPot(int itemId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(0x12B);
        mplew.writeInt(itemId);
        return mplew.getPacket();
    }

    public static MaplePacket sendAutoMpPot(int itemId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(0x12C);
        mplew.writeInt(itemId);
        return mplew.getPacket();
    }

    public static MaplePacket giveFinalAttack(int skillid, int time) {//packets found by lailainoob
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_BUFF);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.write(0);//some 80 and 0 bs
        mplew.write(0x80);//let's just do 80, then 0
        mplew.writeInt(0);
        mplew.writeShort(1);
        mplew.writeInt(skillid);
        mplew.writeInt(time);
        mplew.writeInt(0);
        return mplew.getPacket();
    }
    /*public static MaplePacket showHPQMoon() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.HPQ_MOON);
    mplew.writeInt(-1);
    return mplew.getPacket();
    }*/

    public static MaplePacket triggerMoon(int oid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.REACTOR_HIT);
        mplew.writeInt(oid);
        mplew.write(6);//state
        mplew.writeShort(-183);
        mplew.writeShort(-433);
        mplew.writeShort(0);
        mplew.write(-1);
        mplew.write(78);
        return mplew.getPacket();
    }

    private static void writeLongMask(MaplePacketLittleEndianWriter mplew, List<Pair<MapleBuffStat, Integer>> statups) {
        long firstmask = 0;
        long secondmask = 0;
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            if (statup.getLeft().isFirst()) {
                firstmask |= statup.getLeft().getValue();
            } else {
                secondmask |= statup.getLeft().getValue();
            }
        }
        mplew.writeLong(firstmask);
        mplew.writeLong(secondmask);
    }

    // List<Pair<MapleDisease, Integer>>
    private static void writeLongDiseaseMask(MaplePacketLittleEndianWriter mplew, List<Pair<MapleDisease, Integer>> statups) {
        long firstmask = 0;
        long secondmask = 0;
        for (Pair<MapleDisease, Integer> statup : statups) {
            if (statup.getLeft().isFirst()) {
                firstmask |= statup.getLeft().getValue();
            } else {
                secondmask |= statup.getLeft().getValue();
            }
        }
    }

    public static MaplePacket updateAriantPQRanking(String name, int score, boolean empty) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ARIANT_PQ_START);
        mplew.write(empty ? 0 : 1);
        if (!empty) {
            mplew.writeMapleAsciiString(name);
            mplew.writeInt(score);
        }
        return mplew.getPacket();
    }

    public static MaplePacket updateMount(MapleCharacter chr, boolean levelup) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_MOUNT);
        mplew.writeInt(chr.getId());
        mplew.writeInt(chr.getMount().getLevel());
        mplew.writeInt(chr.getMount().getExp());
        mplew.writeInt(chr.getMount().getFatigue());
        mplew.write(levelup ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket MulungEnergy(int energy) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.ENERGY);
        mplew.writeMapleAsciiString("energy");
        mplew.writeMapleAsciiString(String.valueOf(energy));
        return mplew.getPacket();
    }

    public static MaplePacket serverBlocked(int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPEN);
        mplew.write(type);
        return mplew.getPacket();
    }

    public static final MaplePacket getShowFameGain(final int gain) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(4);
        mplew.writeInt(gain);
        return mplew.getPacket();
    }

    public static final MaplePacket updateInfoQuest(final int quest, final String data) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO);
        mplew.write(0x0A);
        mplew.writeShort(quest);
        mplew.writeMapleAsciiString(data);

        return mplew.getPacket();
    }

    public static MaplePacket showOwnBerserk(int skilllevel, boolean Berserk) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
        mplew.write(1);
        mplew.writeInt(1320006);
        mplew.write(skilllevel);
        mplew.write(Berserk ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket showBerserk(int cid, int skilllevel, boolean Berserk) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT);
        mplew.writeInt(cid);
        mplew.write(1);
        mplew.writeInt(1320006);
        mplew.write(skilllevel);
        mplew.write(Berserk ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket summonSkill(int cid, int summonSkillId, int newStance) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SUMMON_SKILL);
        mplew.writeInt(cid);
        mplew.writeInt(summonSkillId);
        mplew.write(newStance);

        return mplew.getPacket();
    }

    public static MaplePacket finishedSort(int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FINISH_SORT);
        mplew.write(0);
        mplew.write(type);
        return mplew.getPacket();
    }

    public static MaplePacket finishedSort2(int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FINISH_SORT2);
        mplew.write(0);
        mplew.write(type);
        return mplew.getPacket();
    }

    public static MaplePacket showRewardItemAnimation(int itemId, String effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
        mplew.write(0x0E);
        mplew.writeInt(itemId);
        mplew.write(1);
        mplew.writeMapleAsciiString(effect);

        return mplew.getPacket();
    }

    public static MaplePacket showRewardItemAnimation(int itemId, String effect, int from_playerid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT);
        mplew.writeInt(from_playerid);
        mplew.write(0x0E);
        mplew.writeInt(itemId);
        mplew.write(1);
        mplew.writeMapleAsciiString(effect);

        return mplew.getPacket();
    }

    public static MaplePacket catchMonster(int mobid, int itemid, byte success) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        if (itemid == 2270002) {
            mplew.writeShort(SendPacketOpcode.CATCH_ARIANT);
        } else {
            mplew.writeShort(SendPacketOpcode.CATCH_MOUNT);
        }
        mplew.writeInt(mobid);
        mplew.writeInt(itemid);
        mplew.write(success);

        return mplew.getPacket();
    }

    public static MaplePacket getGachaponMega(final String name, final String message, final IItem item, final byte rareness) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE);
        mplew.write(rareness == 2 ? 15 : 14);
        mplew.writeMapleAsciiString(name + message);
        mplew.writeInt(0); // 0~3 i think
        mplew.writeMapleAsciiString(name);
        PacketHelper.addItemInfo(mplew, item, true, true);

        return mplew.getPacket();
    }

    public static MaplePacket reportResponse(byte mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.REPORT_RESPONSE);
        mplew.write(mode);
        if (mode == 2) {
            mplew.write(1); //does this change?
            mplew.writeInt(69);
        }
        return mplew.getPacket();
    }

    public static final MaplePacket ItemMaker_Success() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
//D6 00 00 00 00 00 01 00 00 00 00 DC DD 40 00 01 00 00 00 01 00 00 00 8A 1C 3D 00 01 00 00 00 00 00 00 00 00 B0 AD 01 00
        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
        mplew.write(0x12);
        mplew.writeZeroBytes(4);
        return mplew.getPacket();
    }

    public static final MaplePacket ItemMaker_Success_3rdParty(final int from_playerid) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT);
        mplew.writeInt(from_playerid);
        mplew.write(0x12);
        mplew.writeZeroBytes(4);
        return mplew.getPacket();
    }

    public static MaplePacket sendEngagementRequest(String name) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FAMILY_ACTION); //<name> has requested engagement. Will you accept this proposal?
        mplew.write(0);
        mplew.writeMapleAsciiString(name); // name
        mplew.writeInt(10); // playerid
        return mplew.getPacket();
    }

    public static MaplePacket sendGroomWishlist() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.FAMILY_ACTION); //<name> has requested engagement. Will you accept this proposal?
        mplew.write(9);
        return mplew.getPacket();
    }
}
