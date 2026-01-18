package com.github.fracture_hikari.maid_agent.ai;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.papi.PapiReplacer;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.fracture_hikari.maid_agent.MaidAgent;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to trigger LLM chat when an async task completes.
 * 
 * Mimics MaidAIChatManager.normalChat() flow exactly to avoid API errors.
 */
public class AIChatCallback {
    private static final Logger LOGGER = MaidAgent.LOGGER;
    private EntityMaid maid;
    private ChatClientInfo clientInfo;
    private MaidAIChatManager chatManager;
    private LLMClient client;

    public AIChatCallback(EntityMaid maid) {
        this(maid, getDefaultClientInfo());
    }

    public AIChatCallback(EntityMaid maid, ChatClientInfo clientInfo) {
        this.maid = maid;
        this.clientInfo = clientInfo;
        this.chatManager = maid.getAiChatManager();
        this.client = chatManager.getLLMSite().client();
    }

    private static ChatClientInfo getDefaultClientInfo() {
        List<String> defaultDesc = new ArrayList<>();
        defaultDesc.add("A fox girl who loves wine.");
        return new ChatClientInfo("en_us", "Wine Fox", defaultDesc);
    }
    /**
     * Notify the LLM that a task has completed.
     * Triggers LLM to generate a natural response about the completed task.
     *
     * @param result The task result message
     */
    public void notifyTaskComplete(String result) {
        try {
            LOGGER.info("AIChatCallback: Triggering LLM with: {}", result);
            triggerNormalChat(result);
        } catch (Exception e) {
            LOGGER.error("AIChatCallback: Failed to notify task complete", e);
        }
    }
    
    /**
     * Mimics MaidAIChatManager.normalChat() exactly.
     * Key points:
     * 1. Build chatCompletion with system prompt + history (descending order)
     * 2. Filter consecutive tool messages from start
     * 3. Add user message AFTER building from history
     * 4. Call client.chat()
     */
    private void triggerNormalChat(String result) {
        try {
            
            // Build message list exactly like getChatCompletion() does
            List<LLMMessage> chatCompletion = getChatCompletion();
            
            if (chatCompletion.isEmpty()) {
                LOGGER.warn("AIChatCallback: No system setting found, cannot proceed");
                return;
            }

            chatCompletion.add(LLMMessage.assistantChat(maid, result));
            LLMConfig config = LLMConfig.normalChat(chatManager.getLLMModel(), maid);
            long key = maid.getChatBubbleManager().addThinkingText("ai.touhou_little_maid.chat.chat_bubble_waiting");

            LLMCallback callback = new LLMCallback(chatManager, result, key);
            client.chat(chatCompletion, config, callback);
            
            LOGGER.info("AIChatCallback: Triggered LLM with {} messages", chatCompletion.size());
            
        } catch (Exception e) {
            LOGGER.error("AIChatCallback: Failed to trigger LLM", e);
        }
    }

    /**
     * copied from com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager private method
     */
    private List<LLMMessage> getChatCompletion() {
        String language = clientInfo.language();
        // 如果含有自定义设定，则直接使用自定义设定
        if (StringUtils.isNotBlank(chatManager.customSetting)) {
            EntityMaid maid = chatManager.getMaid();
            String setting = PapiReplacer.replace(chatManager.customSetting, maid, language);
            CappedQueue<LLMMessage> history = chatManager.getHistory();
            List<LLMMessage> chatList = Lists.newArrayList();
            chatList.add(LLMMessage.systemChat(maid, setting));
            // 倒序遍历，将历史对话加载进去
            history.getDeque().descendingIterator().forEachRemaining(chatList::add);
            return chatList;
        }

        // 其他情况下，获取默认设定文件
        return chatManager.getSetting().map(s -> {
            EntityMaid maid = chatManager.getMaid();
            String setting = s.getSetting(maid, language);
            CappedQueue<LLMMessage> history = chatManager.getHistory();
            List<LLMMessage> chatList = Lists.newArrayList();
            chatList.add(LLMMessage.systemChat(maid, setting));
            // 倒序遍历，将历史对话加载进去
            history.getDeque().descendingIterator().forEachRemaining(chatList::add);
            return chatList;
        }).orElse(Lists.newArrayList());
    }
}
