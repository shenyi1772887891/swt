package com.sy;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class Main {

    public static void main(String[] args) {
        Display display = new Display();
        final Shell shell = new Shell(display);


        shell.setSize(800, 600);

        final TerminalS terminalS = new TerminalS(shell, SWT.NONE);

        terminalS.setSize(800, 600);









        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();



    }

    public static boolean checkDate(int n) {
        if (48 == n || 49 == n || 50 == n || 51 == n || 52 == n || 53 == n || 54 == n				|| 55 == n || 56 == n || 57 == n) {
            return true;
        }
        return false;
    }


}
