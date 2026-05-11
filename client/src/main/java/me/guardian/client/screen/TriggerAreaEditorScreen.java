package me.guardian.client.screen;

import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class TriggerAreaEditorScreen extends Screen {
    private final TriggerArea area;
    private final List<String> commandValues;
    private final List<EditBox> commandFields = new ArrayList<>();
    private EditBox triggerSelectors;
    private EditBox restrictionSelectors;
    private boolean runOnce;
    private String triggerMode;
    private boolean globalRestrictions;
    private String restrictionMode;
    private Button runButton;
    private Button triggerButton;
    private Button globalButton;
    private Button restrictionButton;
    private Button addCommandButton;

    public TriggerAreaEditorScreen(TriggerArea area) {
        super(Component.literal("Trigger Area"));
        this.area = area;
        this.commandValues = new ArrayList<>(area.commands.isEmpty() ? List.of("") : area.commands);
        this.runOnce = area.runOnce;
        this.triggerMode = area.triggerMode;
        this.globalRestrictions = area.globalRestrictions;
        this.restrictionMode = area.restrictionMode;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(600, width - 40);
        int left = (width - panelWidth) / 2;
        int top = 28;
        commandFields.clear();
        int commandFieldWidth = panelWidth - 34;
        for (int i = 0; i < commandValues.size(); i++) {
            EditBox commandField = new EditBox(font, left, top + 28 + i * 24, commandFieldWidth, 20, Component.literal("Console command"));
            commandField.setMaxLength(32767);
            commandField.setValue(commandValues.get(i));
            commandFields.add(commandField);
            addRenderableWidget(commandField);
        }
        addCommandButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> addCommandField())
                .bounds(left + commandFieldWidth + 6, top + 28, 28, 20)
                .build());

        int controlsTop = top + 28 + commandValues.size() * 24 + 28;

        int buttonWidth = (panelWidth - 20) / 3;
        runButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            runOnce = !runOnce;
            updateLabels();
        }).bounds(left, controlsTop, buttonWidth, 20).build());

        triggerButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            triggerMode = "everyone".equals(triggerMode) ? "selectors" : "everyone";
            updateLabels();
        }).bounds(left + buttonWidth + 10, controlsTop, buttonWidth, 20).build());

        globalButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            globalRestrictions = !globalRestrictions;
            updateLabels();
        }).bounds(left + (buttonWidth + 10) * 2, controlsTop, buttonWidth, 20).build());

        triggerSelectors = new EditBox(font, left, controlsTop + 32, panelWidth, 20, Component.literal("Trigger selectors"));
        triggerSelectors.setMaxLength(32767);
        triggerSelectors.setValue(area.triggerSelectors);
        addRenderableWidget(triggerSelectors);

        restrictionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            restrictionMode = switch (restrictionMode) {
                case "everyone" -> "only_matching";
                case "only_matching" -> "except_matching";
                default -> "everyone";
            };
            updateLabels();
        }).bounds(left, controlsTop + 62, 190, 20).build());

        restrictionSelectors = new EditBox(font, left, controlsTop + 92, panelWidth, 20, Component.literal("Restriction selectors"));
        restrictionSelectors.setMaxLength(32767);
        restrictionSelectors.setValue(area.restrictionSelectors);
        addRenderableWidget(restrictionSelectors);

        int bottom = Math.min(height - 36, controlsTop + 124);
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> save()).bounds(left, bottom, 148, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left + 158, bottom, 148, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> delete()).bounds(left + panelWidth - 148, bottom, 148, 20).build());
        updateLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
        graphics.drawString(font, "Set Trigger Area Commands", width / 2 - font.width("Set Trigger Area Commands") / 2, 10, 0xFFFFFF);
        if (!commandFields.isEmpty()) {
            graphics.drawString(font, "Console command", commandFields.get(0).getX(), commandFields.get(0).getY() - 11, 0xA0A0A0);
        }
        if (triggerSelectors.isVisible()) {
            graphics.drawString(font, "Selectors", triggerSelectors.getX(), triggerSelectors.getY() - 10, 0xA0A0A0);
        }
        if (restrictionSelectors.isVisible()) {
            graphics.drawString(font, "Global restriction selectors", restrictionSelectors.getX(), restrictionSelectors.getY() - 10, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        saveCommandValues();
        List<String> commands = commandValues.stream()
                .map(String::trim)
                .filter(command -> !command.isEmpty())
                .toList();
        TriggerArea edited = area.edited(commands, runOnce, triggerMode, triggerSelectors.getValue(),
                globalRestrictions, restrictionMode, restrictionSelectors.getValue());
        ClientPlayNetworking.send(new TriggerAreaPayloads.SaveEditor(edited.serialize()));
        onClose();
    }

    private void delete() {
        ClientPlayNetworking.send(new TriggerAreaPayloads.Delete(area.id));
        onClose();
    }

    private void addCommandField() {
        saveCommandValues();
        commandValues.add("");
        rebuildWidgets();
    }

    private void saveCommandValues() {
        commandValues.clear();
        for (EditBox field : commandFields) {
            commandValues.add(field.getValue());
        }
    }

    private void updateLabels() {
        runButton.setMessage(Component.literal(runOnce ? "Only once" : "Every time"));
        triggerButton.setMessage(Component.literal("Triggered by: " + ("everyone".equals(triggerMode) ? "everyone" : "selectors")));
        globalButton.setMessage(Component.literal(globalRestrictions ? "Global: restricted" : "Global: off"));
        restrictionButton.setMessage(Component.literal("Restrict: " + switch (restrictionMode) {
            case "only_matching" -> "only selectors";
            case "except_matching" -> "except selectors";
            default -> "everyone";
        }));
        triggerSelectors.setVisible(!"everyone".equals(triggerMode));
        restrictionSelectors.setVisible(globalRestrictions && !"everyone".equals(restrictionMode));
    }
}
