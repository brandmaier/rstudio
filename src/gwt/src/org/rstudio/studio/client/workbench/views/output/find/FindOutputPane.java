/*
 * FindOutputPane.java
 *
 * Copyright (C) 2009-19 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.output.find;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import org.rstudio.core.client.CodeNavigationTarget;
import org.rstudio.core.client.command.AppCommand;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.events.EnsureVisibleEvent;
import org.rstudio.core.client.events.HasSelectionCommitHandlers;
import org.rstudio.core.client.events.SelectionCommitEvent;
import org.rstudio.core.client.events.SelectionCommitHandler;
import org.rstudio.core.client.widget.*;
import org.rstudio.core.client.widget.events.SelectionChangedHandler;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.ui.WorkbenchPane;
import org.rstudio.studio.client.workbench.views.output.find.model.FindResult;

import java.util.ArrayList;


public class FindOutputPane extends WorkbenchPane
      implements FindOutputPresenter.Display,
                 HasSelectionCommitHandlers<CodeNavigationTarget>
{
   @Inject
   public FindOutputPane(Commands commands)
   {
      super("Find Results");
      commands_ = commands;
      ensureWidget();
   }

   @Override
   protected Toolbar createMainToolbar()
   {
      Toolbar toolbar = new Toolbar("Find Output Tab");

      searchLabel_ = new Label();
      toolbar.addLeftWidget(searchLabel_);

      viewReplaceButton_ = new SmallButton("Replace");
      toolbar.addLeftWidget(viewReplaceButton_);
      viewReplaceButton_.addClickHandler(new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            toggleReplaceView();
         }
      });

      stopSearch_ = new ToolbarButton(
            ToolbarButton.NoText,
            "Stop find in files",
            commands_.interruptR().getImageResource());
      stopSearch_.setVisible(false);

      toolbar.addRightWidget(stopSearch_);

      return toolbar;
   }

   @Override
   protected SecondaryToolbar createSecondaryToolbar()
   {
      replaceToolbar_ = new SecondaryToolbar("Replace");
      replaceToolbarVisible_ = true;

      replaceLabel_ = new Label();
      replaceToolbar_.addLeftWidget(replaceLabel_);

      replaceTextBox_ = new TextBoxWithCue("Replace all");
      replaceToolbar_.addLeftWidget(replaceTextBox_);

      replaceRegex_ = new CheckBox();
      replaceRegexLabel_ =
                     new CheckboxLabel(replaceRegex_, "Regular expression").getLabel();
      replaceRegex_.getElement().getStyle().setMarginRight(0, Unit.PX);
      replaceToolbar_.addLeftWidget(replaceRegex_);
      replaceRegexLabel_.getElement().getStyle().setMarginRight(9, Unit.PX);
      replaceToolbar_.addLeftWidget(replaceRegexLabel_);

      replaceAllButton_ = new SmallButton("Replace All");
      replaceToolbar_.addLeftWidget(replaceAllButton_);

      /*
      replaceTextBox_.addKeyDownHandler(new KeyDownHandler()
      {
         public void onKeyDown(KeyDownEvent event);
         {
         }
      });
      */

      return replaceToolbar_;
   }

   @Override
   protected Widget createMainWidget()
   {
      context_ = new FindResultContext();

      FindOutputResources resources = GWT.create(FindOutputResources.class);
      resources.styles().ensureInjected();

      table_ = new FastSelectTable<FindResult, CodeNavigationTarget, Object>(
            new FindOutputCodec(resources),
            resources.styles().selectedRow(),
            true,
            false);
      FontSizer.applyNormalFontSize(table_);
      table_.addStyleName(resources.styles().findOutput());
      table_.addClickHandler(new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            if (event.getNativeButton() != NativeEvent.BUTTON_LEFT)
               return;

            if (dblClick_.checkForDoubleClick(event.getNativeEvent()))
               fireSelectionCommitted();
         }

         private final DoubleClickState dblClick_ = new DoubleClickState();
      });

      table_.addKeyDownHandler(new KeyDownHandler()
      {
         @Override
         public void onKeyDown(KeyDownEvent event)
         {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
               fireSelectionCommitted();
            event.stopPropagation();
            event.preventDefault();
         }
      });

      replaceToolbarVisible_ = false;
      setSecondaryToolbarVisible(replaceToolbarVisible_);

      container_ =  new SimplePanel();
      container_.addStyleName("ace_editor_theme");
      container_.setSize("100%", "100%");
      statusPanel_ = new StatusPanel();
      statusPanel_.setSize("100%", "100%");
      scrollPanel_ = new ScrollPanel(table_);
      scrollPanel_.setSize("100%", "100%");
      container_.setWidget(scrollPanel_);
      return container_;
   }

   private void toggleReplaceView()
   {
      replaceToolbarVisible_ = !replaceToolbarVisible_;
      setSecondaryToolbarVisible(replaceToolbarVisible_);

      FindOutputResources resources = GWT.create(FindOutputResources.class);
      if (replaceToolbarVisible_)
      {
         table_.addStyleName(resources.styles().findOutputReplace());
         // !!!
         //table_.clear();
         //table.addItems(findResults.subList(0,matchesToAdd),false);
      }
      else
      {
         table_.removeStyleName(resources.styles().findOutputReplace());
      }
   }

   private void fireSelectionCommitted()
   {
      ArrayList<CodeNavigationTarget> values = table_.getSelectedValues();
      if (values.size() == 1)
         SelectionCommitEvent.fire(this, values.get(0));
   }

   @Override
   public void addMatches(ArrayList<FindResult> findResults)
   {
      int matchesToAdd = Math.min(findResults.size(), MAX_COUNT - matchCount_);

      if (matchesToAdd > 0)
      {
         matchCount_ += matchesToAdd;
         
         if (matchCount_ > 0 && container_.getWidget() != scrollPanel_)
            container_.setWidget(scrollPanel_);
            
         table_.addItems(findResults.subList(0, matchesToAdd), false);
      }
      
      if (matchesToAdd != findResults.size())
         showOverflow();
   }

   @Override
   public void clearMatches()
   {
      context_.reset();
      table_.clear();
      overflow_ = false;
      matchCount_ = 0;
      statusPanel_.setStatusText("");
      container_.setWidget(statusPanel_);
   }
   
   @Override
   public void showSearchCompleted()
   {
      if (matchCount_ == 0)
         statusPanel_.setStatusText("(No results found)");
   }
   
   @Override
   public void onSelected()
   {
      super.onSelected();
      
      table_.focus();
      ArrayList<Integer> indices = table_.getSelectedRowIndexes();
      if (indices.isEmpty())
         table_.selectNextRow();
   }

   @Override
   public void ensureVisible(boolean activate)
   {
      fireEvent(new EnsureVisibleEvent(activate));
   }

   @Override
   public HasClickHandlers getStopSearchButton()
   {
      return stopSearch_;
   }

   @Override
   public void setStopSearchButtonVisible(boolean visible)
   {
      stopSearch_.setVisible(visible);
   }

   @Override
   public void ensureSelectedRowIsVisible()
   {
      ArrayList<TableRowElement> rows = table_.getSelectedRows();
      if (rows.size() > 0)
      {
         DomUtils.ensureVisibleVert(scrollPanel_.getElement(),
                                    rows.get(0),
                                    20);
      }
   }

   @Override
   public HandlerRegistration addSelectionChangedHandler(SelectionChangedHandler handler)
   {
      return table_.addSelectionChangedHandler(handler);
   }

   @Override
   public void showOverflow()
   {
      if (overflow_)
         return;
      overflow_ = true;
      ArrayList<FindResult> items = new ArrayList<FindResult>();
      items.add(null);
      table_.addItems(items, false);
   }

   @Override
   public void updateSearchLabel(String query, String path)
   {
      SafeHtmlBuilder builder = new SafeHtmlBuilder();
      builder.appendEscaped("Results for ")
            .appendHtmlConstant("<strong>")
            .appendEscaped(query)
            .appendHtmlConstant("</strong>")
            .appendEscaped(" in ")
            .appendEscaped(path);
      searchLabel_.getElement().setInnerHTML(builder.toSafeHtml().asString());
   }

   @Override
   public void clearSearchLabel()
   {
      searchLabel_.setText("");
   }

   @Override
   public HandlerRegistration addSelectionCommitHandler(SelectionCommitHandler<CodeNavigationTarget> handler)
   {
      return addHandler(handler, SelectionCommitEvent.getType());
   }

   private class StatusPanel extends HorizontalCenterPanel
   {
      public StatusPanel()
      {
         super(new Label(), 50);
         label_ = (Label)getWidget();
         
      }
      
      public void setStatusText(String status)
      {
         label_.setText(status);
      }
      
      
      private final Label label_;
      
   }
   
   private FastSelectTable<FindResult, CodeNavigationTarget, Object> table_;
   private FindResultContext context_;
   private final Commands commands_;
   private Label searchLabel_;
   private ToolbarButton stopSearch_;
   private SimplePanel container_;
   private ScrollPanel scrollPanel_;
   private StatusPanel statusPanel_;
   private boolean overflow_ = false;
   private int matchCount_;
   private SmallButton viewReplaceButton_;
   private SecondaryToolbar replaceToolbar_;
   private boolean replaceToolbarVisible_;
   private Label replaceLabel_;
   private Label replaceRegexLabel_;
   private CheckBox replaceRegex_;
   private TextBoxWithCue replaceTextBox_;
   private SmallButton replaceAllButton_;

   // This must be the same as MAX_COUNT in SessionFind.cpp
   private static final int MAX_COUNT = 1000;
}
