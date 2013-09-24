//? name=MarkPosition, shortcut=Ctrl+Shift+m, author=odexcide, help=Add
position marking
// Creates marked positions in Disassembly or Decompiled Views by pressing Alt+m
// Delete marks by hitting 'd' in the marked positions table

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import jeb.api.IScript;
import jeb.api.JebInstance;
import jeb.api.ui.CodePosition;
import jeb.api.ui.CodeView;
import jeb.api.ui.JebUI;
import jeb.api.ui.View;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TableItem;

public class MarkPosition implements IScript {

    private String marksFileName = "marks.csv";
    private static final String TAG = MarkPosition.class.getSimpleName();

    private enum ColumnName {Line, Description, View, Signature};
    private enum LogPriority {ERROR, WARN, INFO, DEBUG};

    JebInstance jebInstance;
    JebUI jebUI;
    Display jebDisplay;
    Shell jebMainShell;
    Table jebMarksTable;

    public void run(JebInstance jeb) {
        jebInstance = jeb;
        if(!jebInstance.isFileLoaded()) {
            e("File not loaded!");
            return;
        }

        String inputPath = jebInstance.getInputPath();
        if(inputPath == null) {
            e("Can not obtain input path!");
            return;
        }

        File inputFile = new File(inputPath);

        if(!inputFile.exists()) {
            e("Input file doesn't exist!");
            return;
        }

        marksFileName = inputPath.concat(".marks.csv");

        jebUI = jebInstance.getUI();
        Display.getDefault().asyncExec(new HookDisplayTask());
    }

    private class TableSelectionListener extends SelectionAdapter {
        public void widgetDefaultSelected(SelectionEvent event) {

            if(((TableItem)event.item).getText(ColumnName.View.ordinal()).equals("ASSEMBLY")) {
                jebUI.focusView(View.Type.ASSEMBLY);
                CodeView assemblyView = (CodeView)jebUI.getView(View.Type.ASSEMBLY);

                assemblyView.gotoLine(Integer.parseInt(((TableItem)event.item).getText(ColumnName.Line.ordinal())));
            } else if(((TableItem)event.item).getText(ColumnName.View.ordinal()).equals("JAVA")) {
                jebUI.decompileClass(getClassFromSignature(((TableItem)event.item).getText(ColumnName.Signature.ordinal())), true);
                CodeView javaView = (CodeView)jebUI.getView(View.Type.JAVA);

                javaView.gotoLine(Integer.parseInt(((TableItem)event.item).getText(ColumnName.Line.ordinal())));
            }
        }
    }

    private class TableKeyListener extends KeyAdapter {
        public void keyPressed(KeyEvent event) {
            if(event.character == 'd') {
                TableItem[] tableItems = jebMarksTable.getSelection();
                if(tableItems.length <= 0) {
                    w("Can't delete nothing!");
                    return;
                }

                ArrayList<Mark> marks = Mark.fromTableItems(tableItems);

                BufferedReader br = null;
                ArrayList<String> lines = new ArrayList<String>();
                File file = new File(marksFileName);
                try {
                    FileReader fr = new FileReader(file);
                    br = new BufferedReader(fr);

                    String currentLine;
                    while((currentLine = br.readLine()) != null) {
                        d("CurrentLine: " + currentLine);
                        for(TableItem item: tableItems) {
                            d("CurrentItem: " + Mark.fromTableItem(item).serialize().replace("\n",""));

                            if(!currentLine.equals(Mark.fromTableItem(item).serialize().replace("\n",""))) {
                                lines.add(currentLine);
                            } else {
                                d("Deleting line: " + currentLine);
                            }
                        }
                    }
                } catch (IOException e) {
                    e("Error while deleting marks: " + marks);
                } finally {
                    try {
                        if(br != null) { br.close(); }
                    } catch (IOException e) {
                        e("Error closing file handle!");
                    }
                }

                BufferedWriter bw = null;
                try {
                    FileWriter fw = new FileWriter(file);
                    bw = new BufferedWriter(fw);

                    for(String line : lines) {
                        bw.write(line);
                        bw.newLine();
                    }
                } catch (IOException e) {
                    e("Error while deleting marks: " + marks);
                } finally {
                    try {
                        if(bw != null) { bw.close(); }
                    } catch (IOException e) {
                        e("Error closing file handle!");
                    }
                }

                // Now remove from the table
                for(TableItem item : tableItems) {
                    jebMarksTable.remove(jebMarksTable.indexOf(item));
                }
            }
        }
    }

    private class MarkPositionDialog extends Dialog {
        String response;

        public MarkPositionDialog(Shell s) {
            super(s);
            response = null;
        }

        public String open() {
            Shell parent = this.getParent();
            final Shell shell = new Shell(parent, SWT.TITLE | SWT.BORDER | SWT.APPLICATION_MODAL);
            shell.setText("Marked Position");
            shell.setLayout(new GridLayout(2, true));

            Label label = new Label(shell, SWT.NULL);
            label.setText("Enter mark description");

            final Text text = new Text(shell, SWT.SINGLE | SWT.BORDER);
            text.setLayoutData(new GridData(500, 20));

            Button buttonOK = new Button(shell, SWT.PUSH);
            buttonOK.setText("OK");
            buttonOK.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

            Button buttonCancel = new Button(shell, SWT.PUSH);
            buttonCancel.setText("CANCEL");

            // Add all the listeners to the widgets
            text.addListener(SWT.Modify, new Listener() {
                public void handleEvent(Event arg0) {
                    response = text.getText();
                }});
            text.addSelectionListener(new SelectionListener() {
                public void widgetSelected(SelectionEvent arg0) {}

                public void widgetDefaultSelected(SelectionEvent arg0) {
                    response = text.getText();
                    shell.dispose();
                }
            });

            buttonOK.addListener(SWT.Selection, new Listener() {
                public void handleEvent(Event arg0) {
                    shell.dispose();
                }});

            buttonCancel.addListener(SWT.Selection, new Listener() {
                public void handleEvent(Event arg0) {
                    shell.dispose();
                    response = null;
                }});

            shell.addListener(SWT.Traverse, new Listener() {
                public void handleEvent(Event event) {
                    if(event.detail == SWT.TRAVERSE_ESCAPE) {
                        event.doit = false;
                    }
                }
            });

            shell.pack();
            shell.open();

            Display display = parent.getDisplay();
            while(!shell.isDisposed()) {
                if(!display.readAndDispatch()) {
                    display.sleep();
                }
            }

            return response;
        }
    }

    private class ViewKeyListener implements Listener {
        public void handleEvent(Event event) {
            if(event.stateMask == SWT.ALT && event.keyCode == 109) {
                CodeView view = null;
                String currentView = null;
        
                if(jebUI.isViewVisible(View.Type.JAVA)) {
                    view = (CodeView) jebUI.getView(View.Type.JAVA);
                    currentView = View.Type.JAVA.name();
                } else if(jebUI.isViewVisible(View.Type.ASSEMBLY)) {
                    view = (CodeView) jebUI.getView(View.Type.ASSEMBLY);
                    currentView = View.Type.ASSEMBLY.name();
                } else {
                    w("View not handled to create marks.");
                    return;
                }

                int line = view.getCaretLine();
                if(line < 0) {
                    e("Unable to obtain line number from code view!");
                    return;
                }

                CodePosition position = view.getCodePosition(line);
                if(position == null) {
                    e("Unable to get the code position!");
                    return;
                }

                String signature = position.getSignature();

                MarkPositionDialog dialog = new MarkPositionDialog(jebMainShell);
                String description = dialog.open();

                // Check to see if the dialog was cancelled
                if(description == null) {
                    d("Mark was cancelled.");
                    return;
                }

                // Create the Mark
                Mark mark = new Mark(signature, Integer.toString(line), currentView, description);
                writeMark(mark);
            }
        }
    }

    private class HookDisplayTask implements Runnable {
        public void run() {
            jebDisplay = Display.getDefault();
            jebMainShell = jebDisplay.getShells()[0];
            jebDisplay.addFilter(SWT.KeyDown, new ViewKeyListener());

            CTabFolder tabFolder = (CTabFolder) getInstance(jebMainShell, CTabFolder.class);

            if(tabFolder == null) {
                e("tabfolder is null!");
            }

            Table newTable = new Table(tabFolder, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
            newTable.setLinesVisible(true);
            newTable.setHeaderVisible(true);
            newTable.addSelectionListener(new TableSelectionListener());
            newTable.addKeyListener(new TableKeyListener());

            GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
            newTable.setLayoutData(gridData);

            for(ColumnName column: ColumnName.values()) {
                TableColumn newColumn = new TableColumn(newTable, SWT.NONE);
                newColumn.setText(column.name());
            }

            BufferedReader br = null;
            try {
                File file = new File(marksFileName);

                if(!file.exists()) {
                    w("No marked positions to load.");
                }

                FileReader fr = new FileReader(file.getAbsoluteFile());
                br = new BufferedReader(fr);
                i("Loading marked positions from: " + file.getCanonicalPath());

                String currentLine;
                while((currentLine = br.readLine()) != null) {
                    Mark mark = Mark.deserialize(currentLine);
                    TableItem newTableItem = new TableItem(newTable, SWT.NONE);
                    if(mark == null) {
                        e("Malformed mark: " + currentLine);
                        continue;
                    }
                    newTableItem.setText(ColumnName.Signature.ordinal(), mark.signature);
                    newTableItem.setText(ColumnName.Line.ordinal(), mark.line);
                    newTableItem.setText(ColumnName.Description.ordinal(), mark.description);
                }
            } catch(IOException e) {
                e("Error while reading file " + marksFileName);
            } finally {
                try {
                    if(br != null) { br.close(); }
                } catch (IOException e) {
                    e("Error closing file handle!");
                }
            }

            for(ColumnName cn : ColumnName.values()) {
                newTable.getColumn(cn.ordinal()).pack();
            }

            CTabItem newTab = new CTabItem(tabFolder, SWT.NONE);
            newTab.setText("Marks");
            newTab.setControl(newTable);
            jebMarksTable = newTable;
        }
    }

    public static class Mark {
        String signature;
        String line;
        String view;
        String description;

        public Mark(String s, String l, String v) {
            signature = s;
            line = l;
            view = v;
            description = "";
        }

        public Mark(String s, String l, String v, String d) {
            signature = s;
            line = l;
            view = v;
            description = d;
        }

        public String serialize() {
            return String.format("%s,%s,%s,%s\n", signature, line, view, description);
        }

        public static Mark deserialize(String line) {
            String[] fields = line.replaceAll("\n", "").split(",");

            if(fields.length == 4) {
                return new Mark(fields[0], fields[1], fields[2], fields[3]);
            } else {
                return null;
            }
        }

        public static ArrayList<String> serializeAll(ArrayList<Mark> marks) {
            ArrayList<String> strings = new ArrayList<String>();
            for(Mark mark: marks) {
                strings.add(mark.serialize());
            }

            return strings;
        }

        public static Mark fromTableItem(TableItem item) {
            return new Mark(item.getText(ColumnName.Signature.ordinal()),
                item.getText(ColumnName.Line.ordinal()),
                item.getText(ColumnName.View.ordinal()),
                item.getText(ColumnName.Description.ordinal()));
        }

        public static ArrayList<Mark> fromTableItems(TableItem[] items) {
            ArrayList<Mark> marks = new ArrayList<Mark>();
            for(TableItem item : items) {
                marks.add(fromTableItem(item));
            }
            return marks;
        }

        @Override
        public String toString() {
            return String.format("Mark:\n\tSignature:\t%s\n\tLine:\t%s\n\tView:\t%s\n\tDescription:\t%s\n",
                signature,
                line,
                view,
                description);
        }
    }

    private Object getInstance(Object obj, Class<?> clazz) {
        if(clazz.isInstance(obj)) {
            return obj;
        } else if(obj instanceof Composite) {
            for(Object item : ((Composite)obj).getChildren()) {
                Object r = getInstance(item, clazz);
                if(clazz.isInstance(r)) {
                    return r;
                }
            }
        }
        return null;
    }

    private void log(LogPriority priority, String message) {
        if(priority != null) {
            jebInstance.print(String.format("[%s] [%s]\t%s", TAG, priority.name(), message));
        } else {
            jebInstance.print(String.format("[%s]\t%s", TAG, message));
        }
    }

    private void d(String msg) { log(LogPriority.DEBUG, msg); }
    private void i(String msg) { log(LogPriority.INFO, msg); }
    private void w(String msg) { log(LogPriority.WARN, msg); }
    private void e(String msg) { log(LogPriority.ERROR, msg); }

    private void writeMark(Mark mark) {
        // First write the mark to the file
        BufferedWriter bw = null;
        try {
            File file = new File(marksFileName);

            FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
            bw = new BufferedWriter(fw);
            bw.write(mark.serialize());
            bw.close();
        } catch (IOException e) {
            e("Error while writing file " + marksFileName);
        } finally {
            try {
                if(bw != null) { bw.close(); }
            } catch (IOException e) {
                e("Error closing file handle!");
            }
        }

        // Write the mark to the display table
        if(jebMarksTable != null && mark != null) {
            TableItem newTableItem = new TableItem(jebMarksTable, SWT.NONE);
            newTableItem.setText(ColumnName.Signature.ordinal(), mark.signature);
            newTableItem.setText(ColumnName.Line.ordinal(), mark.line);
            newTableItem.setText(ColumnName.View.ordinal(), mark.view);
            newTableItem.setText(ColumnName.Description.ordinal(), mark.description);

            for(ColumnName cn : ColumnName.values()) {
                jebMarksTable.getColumn(cn.ordinal()).pack();
            }
        } else {
            w("Can't obtain mark table to update!");
        }

        i(String.format("Marked %s at %s", mark.signature, mark.line));
    }

    private String getClassFromSignature(String sig) {
            // get the right part of the ; and the right part of the $
            return sig.split(";")[0].split("$")[0] + ";";
    }
}
