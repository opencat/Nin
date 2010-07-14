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
package net.sf.odinms.client.Inventory;

import java.io.Serializable;

public class Item implements IItem, Serializable {

    private final int id;
    private short position;
    private short quantity;
    private byte flag;
    private long expiration = -1;
    private MaplePet pet = null;
    private int uniqueid = 0;
    private String owner = "";
    private String GameMaster_log = null;

    public Item(final int id, final short position, final short quantity, final byte flag) {
	super();
	this.id = id;
	this.position = position;
	this.quantity = quantity;
	this.pet = null;
	this.flag = flag;
    }

    public IItem copy() {
	final Item ret = new Item(id, position, quantity, flag);
	ret.pet = pet;
	ret.owner = owner;
	ret.GameMaster_log = GameMaster_log;
	ret.expiration = expiration;
	return ret;
    }

    public final void setPosition(final short position) {
	this.position = position;
	if (pet != null) {
	    pet.setInventoryPosition(position);
	}
    }

    public void setQuantity(final short quantity) {
	this.quantity = quantity;
    }

    @Override
    public final int getItemId() {
	return id;
    }

    @Override
    public final short getPosition() {
	return position;
    }

    @Override
    public final byte getFlag() {
	return flag;
    }

    @Override
    public final short getQuantity() {
	return quantity;
    }

    @Override
    public byte getType() {
	return 2; // An Item
    }

    @Override
    public final String getOwner() {
	return owner;
    }

    public final void setOwner(final String owner) {
	this.owner = owner;
    }

    public final void setFlag(final byte flag) {
	this.flag = flag;
    }

    @Override
    public final long getExpiration() {
	return expiration;
    }

    public final void setExpiration(final long expire) {
	this.expiration = expire;
    }

    @Override
    public final String getGMLog() {
	return GameMaster_log;
    }

    @Override
    public void setGMLog(final String GameMaster_log) {
	this.GameMaster_log = GameMaster_log;
    }

    public final int getUniqueId() {
	return uniqueid;
    }

    public final void setUniqueId(final int id) {
	this.uniqueid = id;
    }

    public final MaplePet getPet() {
	return pet;
    }

    public final void setPet(final MaplePet pet) {
	this.pet = pet;
    }

    @Override
    public int compareTo(IItem other) {
	if (Math.abs(position) < Math.abs(other.getPosition())) {
	    return -1;
	} else if (Math.abs(position) == Math.abs(other.getPosition())) {
	    return 0;
	} else {
	    return 1;
	}
    }

    @Override
    public String toString() {
	return "Item: " + id + " quantity: " + quantity;
    }
}

