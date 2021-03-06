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
package net.sf.odinms.server.shops;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import net.sf.odinms.client.Inventory.IItem;
import net.sf.odinms.client.Inventory.ItemFlag;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.server.constants.GameConstants;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.tools.Packets.PlayerShopPacket;


public class HiredMerchant extends AbstractPlayerStore {

    public ScheduledFuture<?> schedule;
    private MapleMap map;
    private int channel, storeid;
    private long start;

    public HiredMerchant(MapleCharacter owner, int itemId, String desc) {
	super(owner, itemId, desc);
	start = System.currentTimeMillis();
	this.map = owner.getMap();
	this.channel = owner.getClient().getChannel();
	this.schedule = TimerManager.getInstance().schedule(new Runnable() {

	    @Override
	    public void run() {
		closeShop(true, true);
	    }
	}, 1000 * 60 * 60 * 24);
    }

    public byte getShopType() {
	return IMaplePlayerShop.HIRED_MERCHANT;
    }

    public final void setStoreid(final int storeid) {
	this.storeid = storeid;
    }

    @Override
    public void buy(MapleClient c, int item, short quantity) {
	final MaplePlayerShopItem pItem = items.get(item);
	final IItem shopItem = pItem.item;
	final IItem newItem = shopItem.copy();
	final short perbundle = newItem.getQuantity();

	newItem.setQuantity((short) (quantity * perbundle));

	byte flag = newItem.getFlag();

	if (ItemFlag.KARMA_EQ.check(flag)) {
	    newItem.setFlag((byte) (flag - ItemFlag.KARMA_EQ.getValue()));
	} else if (ItemFlag.KARMA_USE.check(flag)) {
	    newItem.setFlag((byte) (flag - ItemFlag.KARMA_USE.getValue()));
	}

	if (MapleInventoryManipulator.addFromDrop(c, newItem, false)) {
	    pItem.bundles -= quantity; // Number remaining in the store

	    final int gainmeso = getMeso() + (pItem.price * quantity);
	    setMeso(gainmeso - GameConstants.EntrustedStoreTax(gainmeso));
	    c.getPlayer().gainMeso(-pItem.price * quantity, false);
	} else {
	    c.getPlayer().dropMessage(1, "Your inventory is full.");
	}
    }

    @Override
    public void closeShop(boolean saveItems, boolean remove) {
	if (schedule != null) {
	    schedule.cancel(false);
	}
	if (saveItems) {
	    saveItems();
	}
	if (remove) {
	    ChannelServer.getInstance(channel).removeMerchant(this);
	    map.broadcastMessage(PlayerShopPacket.destroyHiredMerchant(getOwnerId()));
	}
	map.removeMapObject(this);

	map = null;
	schedule = null;
    }

    public int getTimeLeft() {
	return (int) ((System.currentTimeMillis() - start) / 1000);
    }

    public MapleMap getMap() {
	return map;
    }

    public final int getStoreId() {
	return storeid;
    }

    @Override
    public MapleMapObjectType getType() {
	return MapleMapObjectType.HIRED_MERCHANT;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
	client.getSession().write(PlayerShopPacket.destroyHiredMerchant(getOwnerId()));
    }

    @Override
    public void sendSpawnData(MapleClient client) {
	client.getSession().write(PlayerShopPacket.spawnHiredMerchant(this));
    }    
}
