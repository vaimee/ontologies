package com.vaimee.protege.graph;

import org.protege.editor.core.editorkit.EditorKit;
import org.protege.editor.core.ui.workspace.WorkspaceTab;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.util.Map;

public class OntologyGraphTab extends WorkspaceTab {

    private static final Logger logger = LoggerFactory.getLogger(OntologyGraphTab.class);
    private OntologyGraphPanel graphPanel;
    private NamespaceLegendPanel namespaceLegendPanel;
    private JSplitPane splitPane;
    private JLabel summaryLabel;
    private OWLModelManager modelManager;

    public OntologyGraphTab() {
        setToolTipText("Visual ontology graph for the active OWL ontology");
    }

    @Override
    public void initialise() {
        EditorKit editorKit = getWorkspace().getEditorKit();
        if (!(editorKit instanceof OWLEditorKit)) {
            throw new IllegalStateException("VAIMEE Graph requires an OWL editor kit");
        }

        modelManager = ((OWLEditorKit) editorKit).getOWLModelManager();
        setLayout(new BorderLayout());

        graphPanel = new OntologyGraphPanel();
        namespaceLegendPanel = new NamespaceLegendPanel();
        summaryLabel = new JLabel();
        JButton refreshButton = new JButton("Refresh graph");
        JButton resetButton = new JButton("Reset view");
        refreshButton.addActionListener(event -> refreshGraph());
        resetButton.addActionListener(event -> resetGraphView());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(refreshButton);
        toolbar.add(resetButton);
        toolbar.add(summaryLabel);

        add(toolbar, BorderLayout.NORTH);
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(namespaceLegendPanel), graphPanel);
        splitPane.setResizeWeight(0.0);
        splitPane.setOneTouchExpandable(true);
        add(splitPane, BorderLayout.CENTER);

        refreshGraph();
        logger.info("VAIMEE ontology graph tab initialized");
    }

    @Override
    public void save() {
        // This tab has no persistent UI state yet.
    }

    @Override
    public void dispose() {
        removeAll();
        graphPanel = null;
        namespaceLegendPanel = null;
        splitPane = null;
        summaryLabel = null;
        modelManager = null;
        logger.info("VAIMEE ontology graph tab disposed");
    }

    private void refreshGraph() {
        logger.info("Refreshing VAIMEE ontology graph");
        OntologyGraph graph = OntologyGraphExtractor.extract(modelManager);
        Map<String, Color> namespaceColors = NamespaceColors.forGraph(graph);
        namespaceLegendPanel.setNamespaces(namespaceColors);
        splitPane.setDividerLocation(namespaceLegendPanel.getPreferredSize().width + 24);
        graphPanel.setGraph(graph, namespaceColors);
        summaryLabel.setText(graph.getNodes().size() + " nodes, " + graph.getEdges().size() + " edges");
        logger.info("VAIMEE ontology graph refreshed: {} nodes, {} edges", graph.getNodes().size(), graph.getEdges().size());
    }

    private void resetGraphView() {
        logger.info("Resetting VAIMEE ontology graph view");
        graphPanel.resetView();
    }
}
