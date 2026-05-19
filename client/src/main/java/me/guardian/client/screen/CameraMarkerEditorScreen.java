package me.guardian.client.screen;

import me.guardian.network.CameraPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CameraMarkerEditorScreen extends Screen {
    private final int entityId;
    private final String initialCutsceneId;
    private final int initialIndex;
    private final float initialDuration;

    private EditBox cutsceneIdField;
    private EditBox indexField;
    private EditBox durationField;

    public CameraMarkerEditorScreen(int entityId, String cutsceneId, int index, float duration) {
        super(Component.translatable("screen.guardian_mod.camera_editor"));
        this.entityId = entityId;
        this.initialCutsceneId = cutsceneId;
        this.initialIndex = index;
        this.initialDuration = duration;
    }

    @Override
    protected void init() {
        int panelWidth = 200;
        int left = (width - panelWidth) / 2;
        int y = 40;

        // Cutscene ID field
        cutsceneIdField = new EditBox(font, left, y + 12, panelWidth, 20, Component.translatable("screen.guardian_mod.camera_editor.cutscene_id"));
        cutsceneIdField.setMaxLength(100);
        cutsceneIdField.setValue(initialCutsceneId);
        addRenderableWidget(cutsceneIdField);

        y += 40;

        // Index field
        indexField = new EditBox(font, left, y + 12, panelWidth, 20, Component.translatable("screen.guardian_mod.camera_editor.index"));
        indexField.setMaxLength(5);
        indexField.setValue(String.valueOf(initialIndex));
        indexField.setFilter(text -> text.matches("\\d*"));
        addRenderableWidget(indexField);

        y += 40;

        // Duration field
        durationField = new EditBox(font, left, y + 12, panelWidth, 20, Component.translatable("screen.guardian_mod.camera_editor.duration"));
        durationField.setMaxLength(8);
        durationField.setValue(String.valueOf(initialDuration));
        durationField.setFilter(text -> text.matches("[0-9.]*"));
        addRenderableWidget(durationField);

        y += 45;

        // Buttons
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(left, y, 95, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + 105, y, 95, 20).build());

        y += 25;

        addRenderableWidget(Button.builder(Component.translatable("button.guardian_mod.delete"), button -> delete())
                .bounds(left, y, panelWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
        Component heading = Component.translatable("screen.guardian_mod.camera_editor.title");
        graphics.drawString(font, heading, width / 2 - font.width(heading) / 2, 15, 0xFFFFFF);

        graphics.drawString(font, Component.translatable("screen.guardian_mod.camera_editor.cutscene_id"), cutsceneIdField.getX(), cutsceneIdField.getY() - 10, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.guardian_mod.camera_editor.index"), indexField.getX(), indexField.getY() - 10, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.guardian_mod.camera_editor.duration"), durationField.getX(), durationField.getY() - 10, 0xA0A0A0);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        String cutsceneId = cutsceneIdField.getValue().trim();
        if (cutsceneId.isEmpty()) {
            cutsceneId = "on_diamond_pickup";
        }

        int index = 1;
        try {
            index = Integer.parseInt(indexField.getValue());
        } catch (NumberFormatException ignored) {}

        float duration = 5.0f;
        try {
            duration = Float.parseFloat(durationField.getValue());
        } catch (NumberFormatException ignored) {}

        ClientPlayNetworking.send(new CameraPayloads.SaveEditor(entityId, cutsceneId, index, duration));
        onClose();
    }

    private void delete() {
        ClientPlayNetworking.send(new CameraPayloads.Delete(entityId));
        onClose();
    }
}
