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
package net.sf.odinms.client.messages.commands.Jounin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.GMCommand;
import net.sf.odinms.client.messages.GMCommandDefinition;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.life.MapleMonsterInformationProvider;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;

public class MonsterInfoCommands implements GMCommand {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        Connection con = DatabaseConnection.getConnection();
        MapleMap map = c.getPlayer().getMap();
        double range = Double.POSITIVE_INFINITY;
        if (splitted[0].equals("killall")) {
            if (splitted.length > 1) {
                int irange = Integer.parseInt(splitted[1]);
                if (splitted.length <= 2) {
                    range = irange * irange;
                } else {
                    map = c.getChannelServer().getMapFactory().getMap(Integer.parseInt(splitted[2]));
                }
            }
            MapleMonster mob;
            for (MapleMapObject monstermo : map.getMapObjectsInRange(c.getPlayer().getPosition(), range, Arrays.asList(MapleMapObjectType.MONSTER))) {
                mob = (MapleMonster) monstermo;
                map.killMonster(mob, c.getPlayer(), false, false, (byte) 1);
            }
        } else if (splitted[0].equals("killalldrops")) {
            if (splitted.length > 1) {
                int irange = Integer.parseInt(splitted[1]);
                if (splitted.length <= 2) {
                    range = irange * irange;
                } else {
                    map = c.getChannelServer().getMapFactory().getMap(Integer.parseInt(splitted[2]));
                }
            }
            MapleMonster mob;
            for (MapleMapObject monstermo : map.getMapObjectsInRange(c.getPlayer().getPosition(), range, Arrays.asList(MapleMapObjectType.MONSTER))) {
                mob = (MapleMonster) monstermo;
                map.killMonster(mob, c.getPlayer(), true, false, (byte) 1);
            }
        } else if (splitted[0].equals("killallnospawn")) {
            map.killAllMonsters(false);
        } else if (splitted[0].equals("monsterdebug")) {
            if (splitted.length > 1) {
                int irange = Integer.parseInt(splitted[1]);
                if (splitted.length <= 2) {
                    range = irange * irange;
                } else {
                    map = c.getChannelServer().getMapFactory().getMap(Integer.parseInt(splitted[2]));
                }
            }
            MapleMonster mob;
            for (MapleMapObject monstermo : map.getMapObjectsInRange(c.getPlayer().getPosition(), range, Arrays.asList(MapleMapObjectType.MONSTER))) {
                mob = (MapleMonster) monstermo;
                c.getPlayer().dropMessage(6, "Monster " + mob.toString());
            }
        } else if (splitted[0].equalsIgnoreCase("insertdrop")) {
            if (splitted.length < 3) {
                mc.dropMessage("Syntax : !insertdrop mobid itemid chance");
                return;
            }
            int mid = Integer.parseInt(splitted[1]);
            int iid = Integer.parseInt(splitted[2]);
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            if (ii.getSlotMax(iid) < 1) {
                mc.dropMessage("Seems like you entered wrong itemid");
                return;
            }
            int chance = Integer.parseInt(splitted[3]);
            try {
                PreparedStatement ps = con.prepareStatement("INSERT INTO `monsterdrops` (`monsterid`,`itemid`,`chance`) VALUES (?, ?, ?);");
                ps.setInt(1, mid);
                ps.setInt(2, iid);
                ps.setInt(3, chance);
                ps.executeUpdate();
                mc.dropMessage(" Success ");
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else if (splitted[0].equalsIgnoreCase("removedrop")) {
            try {
                int mid = Integer.parseInt(splitted[1]);
                int iid = Integer.parseInt(splitted[2]);
                PreparedStatement ps = con.prepareStatement("DELETE FROM `monsterdrops` where `monsterid` = ? AND `itemid` = ?");
                ps.setInt(1, mid);
                ps.setInt(2, iid);
                ps.executeUpdate();
                mc.dropMessage(" Success ");
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else if (splitted[0].equalsIgnoreCase("reloaddrops")) {
            MapleMonsterInformationProvider.getInstance().clearDrops();
            mc.dropMessage("Reloaded drops");
        }

    }

    @Override
    public GMCommandDefinition[] getDefinition() {
        return new GMCommandDefinition[]{
                    new GMCommandDefinition("killall", "[range]", ""),
                    new GMCommandDefinition("killall", "[range]", ""),
                    new GMCommandDefinition("killall", "[range]", ""),
                    new GMCommandDefinition("monsterdebug", "[range]", ""),
                    new GMCommandDefinition("insertdrop", "mobid itemid", " Inserts items to the drop data base. Will work after server check"),
                    new GMCommandDefinition("removedrop", "mobid itemid", " Deletes an item from dropping"),
                    new GMCommandDefinition("reloaddrops", "", "reloads all monster drops"),};
    }
}
