/*******************************************************************************
 * Copyright (c) 2017 Veracode, Inc. All rights observed.
 *
 * Available for use by Veracode customers as described in the accompanying license agreement.
 *
 * Send bug reports or enhancement requests to support@veracode.com.
 *
 * See the license agreement for conditions on submitted materials.
 ******************************************************************************/
package cadonuno.pipelinescanautotrigger.ui;


import cadonuno.pipelinescanautotrigger.PipelineScanAutoPrePushHandler;
import cadonuno.pipelinescanautotrigger.pipelinescan.PipelineScanFinding;
import cadonuno.pipelinescanautotrigger.settings.project.ProjectSettingsState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.ui.JBUI;
import io.netty.util.HashedWheelTimer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * This class creates the scan results window Created by Virtusa on 2/22/2017.
 */
public class PipelineScanResultsBarToolWindowFactory implements ToolWindowFactory {
    private static final Color HYPERLINK_COLOR = new JBColor(new Color(1, 108, 93),
            new Color(1, 108, 93));
    private static final String ALL_FINDINGS = "All Findings";
    private static final String FINDINGS_VIOLATING_CRITERIA = "Findings Violating Criteria";
    private static final GridLayoutManager MAIN_PANEL_LAYOUT = new GridLayoutManager(2, 1,
            JBUI.emptyInsets(), -1, -1);

    private static final GridLayout SINGLE_ITEM_LAYOUT = new GridLayout(1, 1);
    private static final int RESULTS_FOOTER_HEIGHT = 35;
    private static final Dimension RESULTS_FOOTER_MINIMUM_SIZE = new Dimension(100, RESULTS_FOOTER_HEIGHT);
    private static final Dimension RESULTS_FOOTER_PREFERRED_SIZE = new Dimension(512, RESULTS_FOOTER_HEIGHT);
    private static final Dimension RESULTS_FOOTER_MAXIMUM_SIZE = new Dimension(1920, RESULTS_FOOTER_HEIGHT);

    private static Color defaultColor = null;
    private static Integer defaultWidth = null;

    public static final int DETAILS_COLUMN_INDEX = 4;
    private final static Map<Project, ToolWindowOwner> projectToToolWindowMap = new HashMap<>();

    private static final DefaultTableCellRenderer TABLE_RENDERER = new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (defaultWidth == null) {
                defaultWidth = component.getWidth();
            }
            if (column == 0) {
                component.setSize(0, component.getHeight());
            }
            if (column == DETAILS_COLUMN_INDEX) {
                component.setForeground(HYPERLINK_COLOR);
            } else if (defaultColor == null) {
                defaultColor = component.getForeground();
            } else {
                component.setForeground(defaultColor);
            }
            return component;
        }
    };

    private static PipelineScanResultsBarToolWindowFactory instance;
    private final Map<Long, String> detailsMap = new HashMap<>();

    public PipelineScanResultsBarToolWindowFactory() {
        instance = this;
    }

    public static PipelineScanResultsBarToolWindowFactory getInstance() {
        return instance;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        initializeTables(project, toolWindow);
        toolWindow.activate(null);
    }

    private void initializeTables(Project project, ToolWindow toolWindow) {
        JBTable allResultsTable = initializeResultsTable(ALL_FINDINGS);
        JBTable filteredResultsTable = initializeResultsTable(FINDINGS_VIOLATING_CRITERIA);

        Content allFindingsParent = toolWindow.getContentManager().getFactory()
                .createContent(getFindingsPanel(project, allResultsTable),
                        ALL_FINDINGS + " (0)",
                        true);
        toolWindow.getContentManager().addContent(allFindingsParent);

        Content filteredFindingsParent = toolWindow.getContentManager().getFactory()
                .createContent(getFindingsPanel(project, filteredResultsTable),
                        FINDINGS_VIOLATING_CRITERIA + " (0)",
                        true);
        toolWindow.getContentManager().addContent(filteredFindingsParent);

        filteredResultsTable.addMouseListener(
                getResultsTableMouseListener(filteredResultsTable)
        );

        allResultsTable.addMouseListener(
                getResultsTableMouseListener(allResultsTable));
        toolWindow.getContentManager().addContent(filteredFindingsParent);
        projectToToolWindowMap.put(project, new ToolWindowOwner(project, toolWindow, allResultsTable,
                filteredResultsTable, filteredFindingsParent, allFindingsParent));
    }

    @NotNull
    private JPanel getFindingsPanel(Project project, JBTable resultsTable) {
        JPanel findingsPanel = new JPanel();
        findingsPanel.setLayout(MAIN_PANEL_LAYOUT);
        findingsPanel.add(initializeScrollPanel(resultsTable),
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        findingsPanel.add(getStartScanButtonPanel(project),
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER,
                        GridConstraints.FILL_BOTH,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK,
                        GridConstraints.ANCHOR_SOUTH,
                        RESULTS_FOOTER_MINIMUM_SIZE,
                        RESULTS_FOOTER_PREFERRED_SIZE,
                        RESULTS_FOOTER_MAXIMUM_SIZE, 0, false));
        return findingsPanel;
    }

    @NotNull
    private MouseAdapter getResultsTableMouseListener(JBTable resultsTable) {
        return new MouseAdapter() {
            public void mousePressed(MouseEvent mouseEvent) {
                mousePressedOnTableEvent(mouseEvent, resultsTable);
            }
        };
    }

    private JPanel initializeScrollPanel(JBTable resultsTable) {
        JPanel resultsScrollPanel = new JPanel();
        JBScrollPane resultsScrollPane = new JBScrollPane(resultsTable);
        resultsScrollPanel.add(resultsScrollPane);
        resultsScrollPanel.setLayout(SINGLE_ITEM_LAYOUT);
        return resultsScrollPanel;
    }

    private JBTable initializeResultsTable(String tableName) {
        JBTable resultsTable = new JBTable();
        resultsTable.setName(tableName);
        resultsTable.setModel(new FindingResultsTableModel());
        resultsTable.setRowSelectionAllowed(true);
        resultsTable.setDefaultRenderer(Object.class, TABLE_RENDERER);
        return resultsTable;
    }

    private JPanel getStartScanButtonPanel(Project project) {
        JButton startScanButton = new JButton("Run Pipeline Scan");
        startScanButton.addActionListener(getStartScanAction(project));
        JPanel buttonParent = new JPanel();
        buttonParent.add(startScanButton);
        buttonParent.setLayout(new FlowLayout(FlowLayout.RIGHT));
        return buttonParent;
    }

    @NotNull
    private ActionListener getStartScanAction(Project project) {
        //TODO: hide the start scan button if the scan is disabled
        return e ->
                Optional.ofNullable(project)
                        .ifPresent(this::startScanOnProject);

    }

    private void startScanOnProject(Project project) {
        PipelineScanAutoPrePushHandler handler =
                PipelineScanAutoPrePushHandler.getProjectHandler(project)
                        .orElseGet(() -> new PipelineScanAutoPrePushHandler(project));

        ProgressWindow progressWindow = new ProgressWindow(true, handler.getProject());
        progressWindow.setTitle("Running pipeline scan");
        ProgressIndicator progressIndicator = new SmoothProgressAdapter(progressWindow, project);

        Runnable outerRunnable = () -> {
            ProgressManager.getInstance().runProcess(() -> {
                try {
                    handler.startScan(progressIndicator);
                } catch (ProcessCanceledException pce) {
                    //process was cancelled, let's just stop
                }
            }, progressIndicator);
        };

        new SwingWorker() {
            @Override
            public Object construct() {
                outerRunnable.run();
                return null;
            }
        }.start();
    }

    private void mousePressedOnTableEvent(MouseEvent mouseEvent, JBTable clickedTable) {
        Point point = mouseEvent.getPoint();
        int row = clickedTable.rowAtPoint(point);
        int col = clickedTable.columnAtPoint(point);
        if (row == -1) {
            return;
        }
        TableModel tableModel = clickedTable.getModel();
        if (col == DETAILS_COLUMN_INDEX) {
            Optional.ofNullable(detailsMap.get((long) tableModel.getValueAt(row, 0)))
                    .ifPresent(DetailsDialog::new);

        } else if (mouseEvent.getClickCount() == 2) {
            getProjectByPath((String) tableModel.getValueAt(row, tableModel.getColumnCount() - 1))
                    .ifPresent(project -> jumpToFinding(project, clickedTable, row));
        }
    }

    private Optional<Project> getProjectByPath(String projectPath) {
        ProjectManager projectManager = ProjectManager.getInstance();
        if (projectManager != null) {
            return Arrays.stream(projectManager.getOpenProjects())
                    .filter(project -> projectPath.equals(project.getProjectFilePath()))
                    .findFirst();
        }
        return Optional.empty();
    }

    private void jumpToFinding(Project project, JBTable clickedTable, int row) {
        String fileName = (String) clickedTable.getModel().getValueAt(row, 5);
        long lineNumber = (long) clickedTable.getModel().getValueAt(row, 6);
        PsiFile[] psiFile;

        if (fileName.contains("/")) {
            String[] nameParts = fileName.split("/");
            fileName = nameParts[nameParts.length - 1];
        }
        psiFile = FilenameIndex.getFilesByName(project, fileName,
                GlobalSearchScope.projectScope(project));

        PsiFile issueFile = null;
        for (PsiFile currentFile : psiFile) {
            if (currentFile.getVirtualFile().getPath().endsWith(fileName)) {
                issueFile = currentFile;
            }
        }
        if (issueFile == null) {
            return;
        }
        OpenFileDescriptor desc =
                new OpenFileDescriptor(project, issueFile.getVirtualFile(), (int) (lineNumber - 1), 0);
        desc.navigate(true);
    }

    public void updateResultsForProject(Project project, List<PipelineScanFinding> results,
                                        List<PipelineScanFinding> filteredResults) {
        ToolWindowOwner toolWindowOwner = projectToToolWindowMap.get(project);
        if (toolWindowOwner == null) {
            return;
        }
        FindingResultsTableModel allFindingsModel = new FindingResultsTableModel();
        results.forEach(element -> {
            detailsMap.put(element.getIssueId(), element.getDetails());
            allFindingsModel.addRow(element.getAsTableRow());
        });
        toolWindowOwner.getAllResultsTable().setModel(allFindingsModel);
        toolWindowOwner.getAllFindingsParent().setDisplayName(ALL_FINDINGS + " (" + results.size() + ")");

        FindingResultsTableModel filteredResultsModel = new FindingResultsTableModel();
        filteredResults.stream()
                .map(PipelineScanFinding::getAsTableRow)
                .forEach(filteredResultsModel::addRow);
        toolWindowOwner.getFilteredResultsTable().setModel(filteredResultsModel);
        toolWindowOwner.getFilteredFindingsParent().setDisplayName(FINDINGS_VIOLATING_CRITERIA + " (" + filteredResults.size() + ")");
    }

    public void init(ToolWindow window) {
    }

    public boolean shouldBeAvailable(@NotNull Project project) {
        return Optional.ofNullable(project.getService(ProjectSettingsState.class))
                .filter(ProjectSettingsState::isEnabled)
                .isPresent();
    }
}