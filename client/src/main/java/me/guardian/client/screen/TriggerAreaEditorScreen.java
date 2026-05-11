package me.guardian.client.screen;

import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public final class TriggerAreaEditorScreen extends Screen {
    private final TriggerArea area;
    private MultiLineEditBox commandsBox;
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

    public TriggerAreaEditorScreen(TriggerArea area) {
        super(Component.literal("Trigger Area"));
        this.area = area;
        this.runOnce = area.runOnce;
        this.triggerMode = area.triggerMode;
        this.globalRestrictions = area.globalRestrictions;
        this.restrictionMode = area.restrictionMode;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, width - 40);
        int left = (width - panelWidth) / 2;
        int top = 24;
        commandsBox = MultiLineEditBox.builder()
                .setX(left)
                .setY(top + 18)
                .setPlaceholder(Component.literal("Commands, one per line"))
                .build(font, panelWidth, 96, Component.literal("Commands"));
        commandsBox.setCharacterLimit(32767);
        commandsBox.setValue(String.join("\n", area.commands));
        addRenderableWidget(commandsBox);

        runButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            runOnce = !runOnce;
            updateLabels();
        }).bounds(left, top + 124, 172, 20).build());

        triggerButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            triggerMode = "everyone".equals(triggerMode) ? "selectors" : "everyone";
            updateLabels();
        }).bounds(left + panelWidth - 172, top + 124, 172, 20).build());

        triggerSelectors = new EditBox(font, left, top + 150, panelWidth, 20, Component.literal("Trigger selectors"));
        triggerSelectors.setMaxLength(32767);
        triggerSelectors.setValue(area.triggerSelectors);
        addRenderableWidget(triggerSelectors);

        globalButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            globalRestrictions = !globalRestrictions;
            updateLabels();
        }).bounds(left, top + 178, 172, 20).build());

        restrictionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            restrictionMode = switch (restrictionMode) {
                case "everyone" -> "only_matching";
                case "only_matching" -> "except_matching";
                default -> "everyone";
            };
            updateLabels();
        }).bounds(left + panelWidth - 172, top + 178, 172, 20).build());

        restrictionSelectors = new EditBox(font, left, top + 204, panelWidth, 20, Component.literal("Restriction selectors"));
        restrictionSelectors.setMaxLength(32767);
        restrictionSelectors.setValue(area.restrictionSelectors);
        addRenderableWidget(restrictionSelectors);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save()).bounds(left, top + 236, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(left + panelWidth - 86, top + 236, 86, 20).build());
        updateLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
        graphics.drawString(font, title, width / 2 - font.width(title) / 2, 10, 0xFFFFFF);
        graphics.drawString(font, "Commands", commandsBox.getX(), commandsBox.getY() - 11, 0xA0A0A0);
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
        List<String> commands = Arrays.stream(commandsBox.getValue().split("\\R"))
                .map(String::trim)
                .filter(command -> !command.isEmpty())
                .toList();
        TriggerArea edited = area.edited(commands, runOnce, triggerMode, triggerSelectors.getValue(),
                globalRestrictions, restrictionMode, restrictionSelectors.getValue());
        ClientPlayNetworking.send(new TriggerAreaPayloads.SaveEditor(edited.serialize()));
        onClose();
    }

    private void updateLabels() {
        runButton.setMessage(Component.literal(runOnce ? "Runs once" : "Runs every time"));
        triggerButton.setMessage(Component.literal("Trigger: " + ("everyone".equals(triggerMode) ? "everyone" : "selectors")));
        globalButton.setMessage(Component.literal(globalRestrictions ? "Restrictions on" : "Restrictions off"));
        restrictionButton.setMessage(Component.literal("Restrict: " + switch (restrictionMode) {
            case "only_matching" -> "only selectors";
            case "except_matching" -> "except selectors";
            default -> "everyone";
        }));
        triggerSelectors.setVisible(!"everyone".equals(triggerMode));
        restrictionSelectors.setVisible(globalRestrictions && !"everyone".equals(restrictionMode));
    }
}
