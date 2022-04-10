package com.sy;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class TerminalS extends StyledText implements VerifyKeyListener, KeyListener, MouseListener, SelectionListener {

    /**
     * 当前所在目录
     */
    private String dir;

    /**
     * 当前命令
     */
    private String command = "";

    /**
     * 当前位置
     */
    private int currentPosition;

    private static final String separator = ">";

    public TerminalS(Composite parent, int style) {
        super(parent, style);

        dir = "test";
        addVerifyKeyListener(this);
        addKeyListener(this);
        addMouseListener(this);
//        addSelectionListener(this);
    }


    @Override
    public void verifyKey(VerifyEvent event) {

        if (event.keyCode != SWT.CTRL && event.character != 'v') {
            // 如果光标不在当前行数，直接跳转到最后，禁止编辑已输出的文本
            // 如果选择了选择了多个文本 编辑时跳转到最后
            if (getSelection().x < (this.getText().length() - command.length()) || getSelectionRange().y > 0) {
                setSelection(getText().length());
            }
        }



        char c = event.character;
        String line = this.getLine(this.getLineCount() - 1);
        // 控制无法删除
        if (line.equals(dir + separator) && c == 8) {
            event.doit = false;
            return;
        }

        // 回车
        if (c == 13) {
            event.doit = false;
            this.append("\n" + "echo > " + command + "\n" + dir + separator);
            command = "";
            setSelection(this.getText().length());
        }

        String text = this.getText();
        System.out.println("ver:" + text);

    }


    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {
        // 每次按键结束后，更新位置
        currentPosition = getSelection().x;
        // 更新当前输入指令
        command = getLine(getLineCount() - 1).replace(dir + separator, "");
    }

    @Override
    public void mouseDoubleClick(MouseEvent e) {

    }

    @Override
    public void mouseDown(MouseEvent e) {

    }

    @Override
    public void mouseUp(MouseEvent e) {
        // 当鼠标点击上方已输出文字时，返回之前的位置。如果是选择了多个字符，可以停留，但无法编辑
        if (getSelection().x < (this.getText().length() - command.length()) && getSelectionRange().y == 0) {
            setSelection(currentPosition);
        }
        if (getSelectionRange().y > 0) {
            StringSelection stringSelection = new StringSelection(getSelectionText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, stringSelection);
        }
    }

    @Override
    public void widgetSelected(SelectionEvent e) {
        StringSelection stringSelection = new StringSelection(e.text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, stringSelection);
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {

    }
}
