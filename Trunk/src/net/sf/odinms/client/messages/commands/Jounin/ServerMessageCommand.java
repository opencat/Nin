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

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.GMCommand;
import net.sf.odinms.client.messages.GMCommandDefinition;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.tools.StringUtil;
import java.util.Collection;
import net.sf.odinms.client.NinjaMS.IRCStuff.MainIRC;
import net.sf.odinms.net.world.WorldServer;

public class ServerMessageCommand implements GMCommand {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splittedLine) throws Exception {
        String msg = StringUtil.joinStringFrom(splittedLine, 1);
        if (msg.equalsIgnoreCase("!array")) {
            msg = WorldServer.getInstance().getArrayString();
        }
        Collection<ChannelServer> cservs = ChannelServer.getAllInstances();
        for (ChannelServer cserv : cservs) {
            cserv.setServerMessage(msg);
        }
        MainIRC.getInstance().setTopic("#ninjams", msg);
    }

    @Override
    public GMCommandDefinition[] getDefinition() {
        return new GMCommandDefinition[]{
                    new GMCommandDefinition("servermessage", "<new message>", "Changes the servermessage to the new message"),};
    }
}
