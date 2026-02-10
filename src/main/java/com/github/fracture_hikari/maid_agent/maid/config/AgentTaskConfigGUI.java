package com.github.fracture_hikari.maid_agent.maid.config;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

@Deprecated
public abstract class AgentTaskConfigGUI extends MaidTaskConfigGui<AgentTaskConfigGUI.Container> {
    public AgentTaskConfigGUI(Container screenContainer, Inventory inv, Component comp) {
        super(screenContainer, inv, comp);
    }
    abstract public static class Container extends TaskConfigContainer{
        public Container(@Nullable MenuType<?> type, int id, Inventory inventory, int entityId) {
            super(type, id, inventory, entityId);
        }
        /*
        public static MenuType<Container> TYPE = IForgeMenuType.create(
                (windowId, inv, data) -> new Container(windowId, inv, data.readInt())
        );
        public Container(int id, Inventory inventory, int entityId) {
            super(TYPE, id, inventory, entityId);
        }

         */
    }
}
