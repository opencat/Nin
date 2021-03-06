/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.odinms.scripting.Map;

import java.awt.Point;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.Skills.SkillFactory;
import net.sf.odinms.net.SendPacketOpcode;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.life.MapleLifeFactory;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Randomizer;
import net.sf.odinms.tools.data.output.MaplePacketLittleEndianWriter;

public class MapScriptMethods {
private static final Point witchTowerPos = new Point(-60, 184);
    private static final String[] mulungEffects = {
	"I have been waiting for you! If you have an ounce of courage in you, you'll be walking in that door right now!",
	"How brave of you to take on Mu Lung Training Tower!",
	"I will make sure you will regret taking on Mu Lung Training Tower!",
	"I do like your intestinal fortitude! But don't confuse your courage with recklessness!",
	"If you want to step on the path to failure, by all means to do so!"};

    private static enum onFirstUserEnter {

	dojang_Eff, PinkBeen_before, onRewordMap, StageMsg_together, NULL;

	private static onFirstUserEnter fromString(String Str) {
	    try {
		return valueOf(Str);
	    } catch (IllegalArgumentException ex) {
		return NULL;
	    }
	}
    };

    private static enum onUserEnter {

	cygnusJobTutorial,
	cygnusTest,
	startEreb,
	dojang_Msg,
	dojang_1st,
	reundodraco,
	undomorphdarco,
	explorationPoint,
	goAdventure,
	go10000,
	go20000,
	go30000,
	go40000,
	go50000,
	go1000000,
	go1010000,
	go1020000,
	goArcher,
	goPirate,
	goRogue,
	goMagician,
	goSwordman,
	iceCave,
	aranDirection,
	rienArrow,
	rien,
	NULL;

	private static onUserEnter fromString(String Str) {
	    try {
		return valueOf(Str);
	    } catch (IllegalArgumentException ex) {
		return NULL;
	    }
	}
    };

    public static void startScript_FirstUser(MapleClient c, String scriptName) {
	switch (onFirstUserEnter.fromString(scriptName)) {
	    case dojang_Eff: {
		int temp = (c.getPlayer().getMapId() - 925000000) / 100;
		int stage = (int) (temp - (Math.floor(temp / 100) * 100));

		sendDojoClock(c, getTiming(stage) * 60);
		sendDojoStart(c, stage - getDojoStageDec(stage));
		break;
	    }
	    case PinkBeen_before: {
		handlePinkBeanStart(c);
		break;
	    }
	    case onRewordMap: {
		reloadWitchTower(c);
		break;
	    }
	    case StageMsg_together: {
		c.getPlayer().getMap().startMapEffect("Solve the question and gather the amount of passes!", 5120017);
		break;
	    }
	    default: {
		System.out.println("Unhandled script : " + scriptName + ", type : onFirstUserEnter");
		break;
	    }
	}
    }

    public static void startScript_User(MapleClient c, String scriptName) {
	switch (onUserEnter.fromString(scriptName)) {
	    case cygnusTest:
	    case cygnusJobTutorial: {
		showIntro(c, "Effect/Direction.img/cygnusJobTutorial/Scene" + (c.getPlayer().getMapId() - 913040100));
		break;
	    }
	    case startEreb: {
		c.getSession().write(MaplePacketCreator.CygnusIntroDisableUI(false));
		c.getSession().write(MaplePacketCreator.CygnusIntroLock(false));
		break;
	    }
	    case dojang_Msg: {
		c.getPlayer().getMap().startMapEffect(mulungEffects[Randomizer.nextInt(mulungEffects.length)], 5120024);
		break;
	    }
	    case dojang_1st: {
		c.getPlayer().writeMulungEnergy();
		break;
	    }
	    case undomorphdarco:
	    case reundodraco: {
		c.getPlayer().cancelEffect(MapleItemInformationProvider.getInstance().getItemEffect(2210016), false, -1);
		break;
	    }
	    case goAdventure: {
		// BUG in MSEA v.91, so let's skip this part.
//		c.getSession().write(MaplePacketCreator.CygnusIntroDisableUI(true));
//		c.getSession().write(MaplePacketCreator.CygnusIntroLock(true));
//		showIntro(c, "Effect/Direction3.img/goAdventure/Scene0");
		final MapleMap mapto = c.getChannelServer().getMapFactory().getMap(10000);
		c.getPlayer().changeMap(mapto, mapto.getPortal(0));
		break;
	    }
	    case go10000:
	    case go20000:
	    case go30000:
	    case go40000:
	    case go50000:
	    case go1000000:
	    case go1010000:
	    case explorationPoint: {
		c.getSession().write(MaplePacketCreator.MapNameDisplay(c.getPlayer().getMapId()));
		break;
	    }
	    case go1020000: {
		c.getSession().write(MaplePacketCreator.CygnusIntroDisableUI(false));
		c.getSession().write(MaplePacketCreator.CygnusIntroLock(false));
		c.getSession().write(MaplePacketCreator.MapNameDisplay(c.getPlayer().getMapId()));
		break;
	    }
	    case goArcher: {
		showIntro(c, "Effect/Direction3.img/archer/Scene0");
		break;
	    }
	    case goPirate: {
		showIntro(c, "Effect/Direction3.img/pirate/Scene0");
		break;
	    }
	    case goRogue: {
		showIntro(c, "Effect/Direction3.img/rogue/Scene0");
		break;
	    }
	    case goMagician: {
		showIntro(c, "Effect/Direction3.img/magician/Scene0");
		break;
	    }
	    case goSwordman: {
		showIntro(c, "Effect/Direction3.img/swordman/Scene0");
		break;
	    }
	    case aranDirection: {
		String data = "";
		switch (c.getPlayer().getMapId()) {
		    case 914090010:
			data = "Effect/Direction1.img/aranTutorial/Scene0";
			break;
		    case 914090011:
			data = "Effect/Direction1.img/aranTutorial/Scene10";
			break;
		    case 914090012:
			data = "Effect/Direction1.img/aranTutorial/Scene20";
			break;
		    case 914090013:
			data = "Effect/Direction1.img/aranTutorial/Scene3";
			break;
		    case 914090100:
			if (c.getPlayer().getGender() == 0) {
			    data = "Effect/Direction1.img/aranTutorial/HandedPoleArm0";
			} else {
			    data = "Effect/Direction1.img/aranTutorial/HandedPoleArm1";
			}
			break;
		    case 914090200:
			data = "Effect/Direction1.img/aranTutorial/Maha";
			break;
		}
		showIntro(c, data);
		break;
	    }
	    case iceCave: {
		c.getPlayer().changeSkillLevel(SkillFactory.getSkill(20000014), (byte) -1, (byte) 0);
		c.getPlayer().changeSkillLevel(SkillFactory.getSkill(20000015), (byte) -1, (byte) 0);
		c.getPlayer().changeSkillLevel(SkillFactory.getSkill(20000016), (byte) -1, (byte) 0);
		c.getPlayer().changeSkillLevel(SkillFactory.getSkill(20000017), (byte) -1, (byte) 0);
		c.getPlayer().changeSkillLevel(SkillFactory.getSkill(20000018), (byte) -1, (byte) 0);
		c.getPlayer().changeSkillLevel(SkillFactory.getSkill(20000014), (byte) -1, (byte) 0);
		showIntro(c, "Effect/Direction1.img/aranTutorial/ClickLirin");
		c.getSession().write(MaplePacketCreator.CygnusIntroDisableUI(false));
		c.getSession().write(MaplePacketCreator.CygnusIntroLock(false));
		break;
	    }
	/*    case rienArrow: {
		if (c.getPlayer().getInfoQuest(21019).equals("miss=o;helper=clear")) {
		    c.getPlayer().updateInfoQuest(21019, "miss=o;arr=o;helper=clear");
		    c.getSession().write(MaplePacketCreator.AranTutInstructionalBalloon("Effect/OnUserEff.img/guideEffect/aranTutorial/tutorialArrow3"));
		}
		break;
	    }
	    case rien: {
		if (c.getPlayer().getQuestStatus(21101) == 2 && c.getPlayer().getInfoQuest(21019).equals("miss=o;arr=o;helper=clear")) {
		    c.getPlayer().updateInfoQuest(21019, "miss=o;arr=o;ck=1;helper=clear");
		}
		c.getSession().write(MaplePacketCreator.CygnusIntroDisableUI(false));
		c.getSession().write(MaplePacketCreator.CygnusIntroLock(false));
		break;
	    }*/
	    default: {
		System.out.println("Unhandled script : " + scriptName + ", type : onUserEnter");
		break;
	    }
	}
    }

    private static final int getTiming(int ids) {
	if (ids <= 5) {
	    return 5;
	} else if (ids >= 7 && ids <= 11) {
	    return 6;
	} else if (ids >= 13 && ids <= 17) {
	    return 7;
	} else if (ids >= 19 && ids <= 23) {
	    return 8;
	} else if (ids >= 25 && ids <= 29) {
	    return 9;
	} else if (ids >= 31 && ids <= 35) {
	    return 10;
	} else if (ids >= 37 && ids <= 38) {
	    return 15;
	}
	return 0;
    }

    private static final int getDojoStageDec(int ids) {
	if (ids <= 5) {
	    return 0;
	} else if (ids >= 7 && ids <= 11) {
	    return 1;
	} else if (ids >= 13 && ids <= 17) {
	    return 2;
	} else if (ids >= 19 && ids <= 23) {
	    return 3;
	} else if (ids >= 25 && ids <= 29) {
	    return 4;
	} else if (ids >= 31 && ids <= 35) {
	    return 5;
	} else if (ids >= 37 && ids <= 38) {
	    return 6;
	}
	return 0;
    }

    private static void showIntro(final MapleClient c, final String data) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT);
	mplew.write(0x14);
	mplew.writeMapleAsciiString(data);

	c.getSession().write(mplew.getPacket());
    }

    private static void sendDojoClock(MapleClient c, int time) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	mplew.writeShort(SendPacketOpcode.CLOCK);
	mplew.write(2);
	mplew.writeInt(time);

	c.getSession().write(mplew.getPacket());
    }

    private static void sendDojoStart(MapleClient c, int stage) {
	c.getSession().write(MaplePacketCreator.environmentChange("Dojang/start", 4));
	c.getSession().write(MaplePacketCreator.environmentChange("dojang/start/stage", 3));
	c.getSession().write(MaplePacketCreator.environmentChange("dojang/start/number/" + stage, 3));

	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

	// 79 00 01 00 01 00 00 00
	mplew.writeShort(SendPacketOpcode.BOSS_ENV);
	mplew.writeShort(1);
	mplew.writeShort(1);
	mplew.writeShort(0);

	c.getSession().write(mplew.getPacket());
    }

    private static void handlePinkBeanStart(MapleClient c) {
	final MapleMap map = c.getPlayer().getMap();
	map.killAllMonsters(true);
	map.respawn(true);

	if (map.containsNPC(2141000) == -1) {
	    map.spawnNpc(2141000, new Point(-190, -42));
	}
    }

    private static void reloadWitchTower(MapleClient c) {
	final MapleMap map = c.getPlayer().getMap();
	map.killAllMonsters(false);

	final int level = c.getPlayer().getLevel();
	int mob;
	if (level <= 10) {
	    mob = 9300367;
	} else if (level <= 20) {
	    mob = 9300368;
	} else if (level <= 30) {
	    mob = 9300369;
	} else if (level <= 40) {
	    mob = 9300370;
	} else if (level <= 50) {
	    mob = 9300371;
	} else if (level <= 60) {
	    mob = 9300372;
	} else if (level <= 70) {
	    mob = 9300373;
	} else if (level <= 80) {
	    mob = 9300374;
	} else if (level <= 90) {
	    mob = 9300375;
	} else if (level <= 100) {
	    mob = 9300376;
	} else {
	    mob = 9300377;
	}
	map.spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(mob), witchTowerPos);
    }
}
