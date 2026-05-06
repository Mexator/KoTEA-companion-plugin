package com.plugin;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ContextPresentationProvider {

    @NotNull
    public static TargetPresentation getPresentation(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        Document document = file != null ? PsiDocumentManager.getInstance(element.getProject()).getDocument(file) : null;

        String code = "";
        String loc = "";

        if (file != null && document != null) {
            int offset = element.getTextOffset();
            int line = document.getLineNumber(offset);
            int lineInd = line + 1;

            loc = file.getName() + ":" + lineInd;

            int startOffset = document.getLineStartOffset(line);
            int endOffset = document.getLineEndOffset(line);

            String lineText = document.getText(new TextRange(startOffset, endOffset)).trim();

            if (lineText.length() > 80) {
                code = lineText.substring(0, 80) + "...";
            } else {
                code = lineText;
            }
        }

        return TargetPresentation.builder(code)
                .locationText(loc)
                .icon(file != null ? file.getIcon(0) : null)
                .presentation();
    }
}