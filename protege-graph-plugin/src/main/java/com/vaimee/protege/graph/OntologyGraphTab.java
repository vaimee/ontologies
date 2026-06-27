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
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class OntologyGraphTab extends WorkspaceTab {

    private static final Logger logger = LoggerFactory.getLogger(OntologyGraphTab.class);
    private OntologyGraphPanel graphPanel;
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
        summaryLabel = new JLabel();
        JButton refreshButton = new JButton("Refresh graph");
        refreshButton.addActionListener(event -> refreshGraph());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(refreshButton);
        toolbar.add(summaryLabel);

        add(toolbar, BorderLayout.NORTH);
        add(graphPanel, BorderLayout.CENTER);

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
        summaryLabel = null;
        modelManager = null;
        logger.info("VAIMEE ontology graph tab disposed");
    }

    private void refreshGraph() {
        logger.info("Refreshing VAIMEE ontology graph");
        OntologyGraph graph = OntologyGraphExtractor.extract(modelManager);
        graphPanel.setGraph(graph);
        summaryLabel.setText(graph.getNodes().size() + " nodes, " + graph.getEdges().size() + " edges");
        logger.info("VAIMEE ontology graph refreshed: {} nodes, {} edges", graph.getNodes().size(), graph.getEdges().size());
    }
}
