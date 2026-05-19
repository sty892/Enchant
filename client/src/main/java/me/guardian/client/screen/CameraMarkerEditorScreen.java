package me.guardian.client.screen;

import me.guardian.network.CameraPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CameraMarkerEditorScreen extends Screen {
    private static final List<String> CUTSCENE_SUGGESTIONS = List.of(
            "on_diamond_pickup",
            "on_border_approach",
            "on_nether_portal_enter"
    );

    private final int entityId;
    private final String initialCutsceneId;
    private final int initialIndex;
    private final float initialDuration;

    private EditBox cutsceneIdField;
    private EditBox indexField;
    private EditBox durationField;

    // Suggestion dropdown state
    private List<String> filteredSuggestions = List.of();
    private boolean showSuggestions = false;

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
        cutsceneIdField.setResponder(value -> updateSuggestions(value));
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

        updateSuggestions(initialCutsceneId);
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

        // Draw suggestion dropdown below cutsceneIdField
        if (showSuggestions && !filteredSuggestions.isEmpty() && cutsceneIdField.isFocused()) {
            int dropX = cutsceneIdField.getX();
            int dropY = cutsceneIdField.getY() + cutsceneIdField.getHeight();
            int dropW = cutsceneIdField.getWidth();
            int itemH = 14;
            int totalH = filteredSuggestions.size() * itemH;

            graphics.fill(dropX, dropY, dropX + dropW, dropY + totalH, 0xE0000000);
            for (int i = 0; i < filteredSuggestions.size(); i++) {
                String suggestion = filteredSuggestions.get(i);
                int itemY = dropY + i * itemH;
                boolean hovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY < itemY + itemH;
                if (hovered) {
                    graphics.fill(dropX, itemY, dropX + dropW, itemY + itemH, 0x80808080);
                }
                graphics.drawString(font, suggestion, dropX + 4, itemY + 3, hovered ? 0xFFFFFF00 : 0xFFE0E0E0);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Check if clicking on a suggestion
        double mouseX = event.x();
        double mouseY = event.y();
        if (showSuggestions && !filteredSuggestions.isEmpty() && cutsceneIdField.isFocused()) {
            int dropX = cutsceneIdField.getX();
            int dropY = cutsceneIdField.getY() + cutsceneIdField.getHeight();
            int dropW = cutsceneIdField.getWidth();
            int itemH = 14;

            if (mouseX >= dropX && mouseX <= dropX + dropW) {
                for (int i = 0; i < filteredSuggestions.size(); i++) {
                    int itemY = dropY + i * itemH;
                    if (mouseY >= itemY && mouseY < itemY + itemH) {
                        cutsceneIdField.setValue(filteredSuggestions.get(i));
                        showSuggestions = false;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateSuggestions(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        filteredSuggestions = CUTSCENE_SUGGESTIONS.stream()
                .filter(s -> s.toLowerCase(java.util.Locale.ROOT).contains(lower) && !s.equals(value))
                .toList();
        showSuggestions = !filteredSuggestions.isEmpty();
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
