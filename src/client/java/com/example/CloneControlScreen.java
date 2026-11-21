package com.example;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CloneControlScreen extends Screen {
    public static boolean isRecording = false;
    public static boolean isPlaying = false;

    public CloneControlScreen() {
        super(Component.literal("Управление клонами и лупом"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 65;
        int btnWidth = 180;
        int btnHeight = 20;
        int spacing = 25;

        this.addRenderableWidget(Button.builder(
                Component.literal(isRecording ? "● Запись: ВКЛ (нажми Стоп)" : "● Начать запись лупа"),
                button -> {
                    isRecording = !isRecording;
                    if (isRecording) {
                        isPlaying = false;
                        sendCommand("cloop rec");
                    } else {
                        sendCommand("cloop stop");
                    }
                    this.rebuildWidgets();
                }
        ).bounds(centerX - btnWidth / 2, startY, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(isPlaying ? "❚❚ Пауза лупа" : "▶ Плей (Запустить луп)"),
                button -> {
                    isPlaying = !isPlaying;
                    if (isPlaying) {
                        isRecording = false;
                        sendCommand("cloop play");
                    } else {
                        sendCommand("cloop pause");
                    }
                    this.rebuildWidgets();
                }
        ).bounds(centerX - btnWidth / 2, startY + spacing, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("■ Стоп и сброс"),
                button -> {
                    isRecording = false;
                    isPlaying = false;
                    sendCommand("cloop stop");
                    this.rebuildWidgets();
                }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 2, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("+ Спавн клона"),
                button -> {
                    sendCommand("spawnclone");
                    this.onClose();
                }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 3, btnWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("✖ Удалить всех клонов"),
                button -> {
                    isRecording = false;
                    isPlaying = false;
                    sendCommand("cloop clear");
                    this.rebuildWidgets();
                }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 4, btnWidth, btnHeight).build());
    }

    private void sendCommand(String command) {
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.connection != null) {
            this.minecraft.player.connection.sendCommand(command);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 90, 0xFFFFFF);

        String status = isRecording ? "Статус: ИДЁТ ЗАПИСЬ" : (isPlaying ? "Статус: ВОСПРОИЗВЕДЕНИЕ" : "Статус: ОЖИДАНИЕ");
        int color = isRecording ? 0xFF5555 : (isPlaying ? 0x55FF55 : 0xAAAAAA);
        graphics.drawCenteredString(this.font, Component.literal(status), this.width / 2, this.height / 2 + 68, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
