package com.vaimee.protege.graph;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import java.awt.Dimension;
import java.util.function.DoubleConsumer;

final class LayoutSettingsPanel extends JPanel {

    LayoutSettingsPanel(OntologyGraphPanel graphPanel) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(16, 12, 12, 12));

        JLabel title = new JLabel("Layout");
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);

        add(createSlider("Charge", -1200, -100, (int) ForceGraphLayout.DEFAULT_CHARGE, value -> graphPanel.setCharge(value)));
        add(createSlider("Link distance", 40, 400, (int) ForceGraphLayout.DEFAULT_LINK_DISTANCE, value -> graphPanel.setLinkDistance(value)));
        add(createSlider("Link strength", 1, 100, (int) (ForceGraphLayout.DEFAULT_LINK_STRENGTH * 100), value -> graphPanel.setLinkStrength(value / 100.0)));
        add(createSlider("Gravity", 1, 100, (int) (ForceGraphLayout.DEFAULT_GRAVITY * 1000), value -> graphPanel.setGravity(value / 1000.0)));
    }

    private JPanel createSlider(String label, int min, int max, int initial, DoubleConsumer onChange) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JLabel nameLabel = new JLabel(label + ": " + initial);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(nameLabel);

        JSlider slider = new JSlider(min, max, initial);
        slider.setAlignmentX(LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, slider.getPreferredSize().height));
        slider.addChangeListener(event -> {
            int value = slider.getValue();
            nameLabel.setText(label + ": " + value);
            if (!slider.getValueIsAdjusting()) {
                onChange.accept(value);
            }
        });
        panel.add(slider);

        return panel;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
