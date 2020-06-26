package org.rstudio.studio.client.panmirror.dialogs;


import org.rstudio.core.client.widget.FormListBox;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.studio.client.panmirror.dialogs.model.PanMirrorInsertCitePreviewPair;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorInsertCiteProps;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorInsertCiteResult;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

public class PanmirrorInsertCiteDialog extends ModalDialog<PanmirrorInsertCiteResult> {

	public PanmirrorInsertCiteDialog(PanmirrorInsertCiteProps citeProps,
			OperationWithInput<PanmirrorInsertCiteResult> operation) {
		super("Insert Citation", Roles.getDialogRole(), operation, () -> {
			operation.execute(null);
		});
		mainWidget_ = GWT.<Binder>create(Binder.class).createAndBindUi(this);

		// TODO: Allow user selection of Bibliography
		citationId_.setText(citeProps.suggestedId);

		// There are no bibliographies, prepopulate the field with
		// an entry to create a new one.
		if (citeProps.bibliographyFiles.length == 0) {
			bibliographies_.addItem("New bibliography (bibliography.bib)");
		} else {
			for (String file : citeProps.bibliographyFiles) {
				bibliographies_.addItem(file);
			}
		}
		
		// Display a preview
		int row = 0;
		for (PanMirrorInsertCitePreviewPair pair: citeProps.previewPairs) {
			row = addPreviewRow(pair.name, pair.value, row);	
		}		
	}
	
	private int addPreviewRow(String label, String value, int row) {
		if (value != null && value.length() > 0) {
			previewTable_.setText(row, 0, label);
			previewTable_.getFlexCellFormatter().addStyleName(row, 0, PanmirrorDialogsResources.INSTANCE.styles().flexTablePreviewName());
			previewTable_.setText(row, 1, value);
			previewTable_.getFlexCellFormatter().addStyleName(row, 1, PanmirrorDialogsResources.INSTANCE.styles().flexTablePreviewValue());
			return ++row;
		}
		return row;
	}

	@Override
	public void focusInitialControl() {
		super.focusInitialControl();
		citationId_.selectAll();
	}

	@Override
	protected PanmirrorInsertCiteResult collectInput() {
		PanmirrorInsertCiteResult result = new PanmirrorInsertCiteResult();
		result.id = citationId_.getText();
		// TODO: allow user selection of the bibliography file and return it
		result.bibliographyFile = "";
		return result;
	}

	@Override
	protected Widget createMainWidget() {
		return mainWidget_;
	}

	@UiField
	TextBox citationId_;
	@UiField
	FormListBox bibliographies_;
	@UiField
	FlexTable previewTable_;

	interface Binder extends UiBinder<Widget, PanmirrorInsertCiteDialog> {
	}

	private Widget mainWidget_;

}