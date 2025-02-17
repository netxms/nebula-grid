/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    chris.gross@us.ibm.com - initial API and implementation
 *******************************************************************************/
package org.eclipse.nebula.widgets.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;

/**
 * <p>
 * NOTE:  THIS WIDGET AND ITS API ARE STILL UNDER DEVELOPMENT.  THIS IS A PRE-RELEASE ALPHA
 * VERSION.  USERS SHOULD EXPECT API CHANGES IN FUTURE VERSIONS.
 * </p>
 *
 * A GridEditor is a manager for a Control that appears above a cell in a Grid
 * and tracks with the moving and resizing of that cell. It can be used to
 * display a text widget above a cell in a Grid so that the user can edit the
 * contents of that cell. It can also be used to display a button that can
 * launch a dialog for modifying the contents of the associated cell.
 *
 * @see org.eclipse.swt.custom.TableEditor
 */
public class GridEditor extends ControlEditor
{
	Grid table;

	GridItem item;

	int column = -1;

	ControlListener columnListener;

	Listener resizeListener;

	private Listener columnVisibleListener;

	private Listener columnGroupListener;

	private SelectionListener scrollListener;

	private TreeListener treeListener;

	/**
	 * Creates a TableEditor for the specified Table.
	 *
	 * @param table the Table Control above which this editor will be displayed
	 */
	public GridEditor(final Grid table)
	{
		super(table);
		this.table = table;

		treeListener = new TreeListener () {
			final Runnable runnable = () -> {
				if (getEditor() == null || getEditor().isDisposed()) {
					return;
				}
				if (table.isDisposed()) {
					return;
				}
				layout();
				getEditor().setVisible(true);
			};
			@Override
			public void treeCollapsed(final TreeEvent e) {
				if (getEditor() == null || getEditor().isDisposed ()) {
					return;
				}
				getEditor().setVisible(false);
				e.display.asyncExec(runnable);
			}
			@Override
			public void treeExpanded(final TreeEvent e) {
				if (getEditor() == null || getEditor().isDisposed ()) {
					return;
				}
				getEditor().setVisible(false);
				e.display.asyncExec(runnable);
			}
		};
		table.addTreeListener(treeListener);

		columnListener = new ControlListener()
		{
			@Override
			public void controlMoved(final ControlEvent e)
			{
				layout();
			}

			@Override
			public void controlResized(final ControlEvent e)
			{
				layout();
			}
		};

		columnVisibleListener = event -> {
			if (getEditor() == null || getEditor().isDisposed ()) {
				return;
			}
			getEditor().setVisible(((GridColumn)event.widget).isVisible());
			if (getEditor().isVisible()) {
				layout();
			}
		};

		resizeListener = event -> layout();

		scrollListener = new SelectionListener()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				layout();
			}
			@Override
			public void widgetDefaultSelected(final SelectionEvent e)
			{
			}
		};

		columnGroupListener = event -> {
			if (getEditor() == null || getEditor().isDisposed()) {
				return;
			}
			getEditor().setVisible(table.getColumn(getColumn()).isVisible());
			if (getEditor().isVisible()) {
				layout();
			}
		};

		// The following three listeners are workarounds for
		// Eclipse bug 105764
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=105764
		table.addListener(SWT.Resize, resizeListener);

		if (table.getVerticalScrollBarProxy() != null)
		{
			table.getVerticalScrollBarProxy().addSelectionListener(scrollListener);
		}
		if (table.getHorizontalScrollBarProxy() != null)
		{
			table.getHorizontalScrollBarProxy().addSelectionListener(scrollListener);
		}

		// To be consistent with older versions of SWT, grabVertical defaults to
		// true
		grabVertical = true;
	}

	/**
	 * Returns the bounds of the editor.
	 *
	 * @return bounds of the editor.
	 */
	protected Rectangle computeEditorBounds()
	{
		if (item == null || column == -1 || item.isDisposed()) {
			return new Rectangle(0, 0, 0, 0);
		}
		final Rectangle cell = item.getBoundsCorrected(column);
		final Rectangle area = table.getClientArea();
		if (cell.x < area.x + area.width)
		{
			if (cell.x + cell.width > area.x + area.width)
			{
				cell.width = area.x + area.width - cell.x;
			}
		}
		final Rectangle editorRect = new Rectangle(cell.x, cell.y, minimumWidth, minimumHeight);

		if (grabHorizontal)
		{
			editorRect.width = Math.max(cell.width, minimumWidth) - (table.getLinesVisible() ? 2 : 0);
		}

		if (grabVertical)
		{
			editorRect.height = Math.max(cell.height, minimumHeight);
		}

		if (horizontalAlignment == SWT.RIGHT)
		{
			editorRect.x += cell.width - editorRect.width;
		}
		else if (horizontalAlignment == SWT.LEFT)
		{
			// do nothing - cell.x is the right answer
		}
		else
		{ // default is CENTER
			editorRect.x += (cell.width - editorRect.width) / 2;
		}

		if (verticalAlignment == SWT.BOTTOM)
		{
			editorRect.y += cell.height - editorRect.height;
		}
		else if (verticalAlignment == SWT.TOP)
		{
			// do nothing - cell.y is the right answer
		}
		else
		{ // default is CENTER
			editorRect.y += (cell.height - editorRect.height) / 2;
		}

		final GridColumn c = table.getColumn(column);

		if( c != null && c.isTree() ) {
			final int x = c.getCellRenderer().getTextBounds(item, false).x;
			editorRect.x += x;
			editorRect.width -= x;
		}

		return editorRect;
	}

	/**
	 * Removes all associations between the TableEditor and the cell in the
	 * table. The Table and the editor Control are <b>not</b> disposed.
	 */
	@Override
	public void dispose()
	{
		if (!table.isDisposed() && column > -1 && column < table.getColumnCount())
		{
			final GridColumn tableColumn = table.getColumn(column);
			tableColumn.removeControlListener(columnListener);
			if (tableColumn.getColumnGroup() != null){
				tableColumn.getColumnGroup().removeListener(SWT.Expand, columnGroupListener);
				tableColumn.getColumnGroup().removeListener(SWT.Collapse, columnGroupListener);
			}
		}

		if (!table.isDisposed())
		{
			table.removeListener(SWT.Resize, resizeListener);

			if (table.getVerticalScrollBarProxy() != null) {
				table.getVerticalScrollBarProxy().removeSelectionListener(scrollListener);
			}

			if (table.getHorizontalScrollBarProxy() != null) {
				table.getHorizontalScrollBarProxy().removeSelectionListener(scrollListener);
			}
		}

		columnListener = null;
		resizeListener = null;
		table = null;
		item = null;
		column = -1;
		super.dispose();
	}

	/**
	 * Returns the zero based index of the column of the cell being tracked by
	 * this editor.
	 *
	 * @return the zero based index of the column of the cell being tracked by
	 * this editor
	 */
	public int getColumn()
	{
		return column;
	}

	/**
	 * Returns the TableItem for the row of the cell being tracked by this
	 * editor.
	 *
	 * @return the TableItem for the row of the cell being tracked by this
	 * editor
	 */
	public GridItem getItem()
	{
		return item;
	}

	/**
	 * Sets the zero based index of the column of the cell being tracked by this
	 * editor.
	 *
	 * @param column the zero based index of the column of the cell being
	 * tracked by this editor
	 */
	public void setColumn(final int column)
	{
		final int columnCount = table.getColumnCount();
		// Separately handle the case where the table has no TableColumns.
		// In this situation, there is a single default column.
		if (columnCount == 0)
		{
			this.column = column == 0 ? 0 : -1;
			layout();
			return;
		}
		if (this.column > -1 && this.column < columnCount)
		{
			final GridColumn tableColumn = table.getColumn(this.column);
			tableColumn.removeControlListener(columnListener);
			tableColumn.removeListener(SWT.Show, columnVisibleListener);
			tableColumn.removeListener(SWT.Hide, columnVisibleListener);
			this.column = -1;
		}

		if (column < 0 || column >= table.getColumnCount()) {
			return;
		}

		this.column = column;
		final GridColumn tableColumn = table.getColumn(this.column);
		tableColumn.addControlListener(columnListener);
		tableColumn.addListener(SWT.Show, columnVisibleListener);
		tableColumn.addListener(SWT.Hide, columnVisibleListener);
		if (tableColumn.getColumnGroup() != null){
			tableColumn.getColumnGroup().addListener(SWT.Expand, columnGroupListener);
			tableColumn.getColumnGroup().addListener(SWT.Collapse, columnGroupListener);
		}
		layout();
	}

	/**
	 * Sets the item that this editor will function over.
	 *
	 * @param item editing item.
	 */
	public void setItem(final GridItem item)
	{
		this.item = item;
		layout();
	}

	/**
	 * Specify the Control that is to be displayed and the cell in the table
	 * that it is to be positioned above.
	 * <p>
	 * Note: The Control provided as the editor <b>must</b> be created with its
	 * parent being the Table control specified in the TableEditor constructor.
	 *
	 * @param editor the Control that is displayed above the cell being edited
	 * @param item the TableItem for the row of the cell being tracked by this
	 * editor
	 * @param column the zero based index of the column of the cell being
	 * tracked by this editor
	 */
	public void setEditor(final Control editor, final GridItem item, final int column)
	{
		setItem(item);
		setColumn(column);
		setEditor(editor);

		layout();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void layout()
	{

		if (table.isDisposed()) {
			return;
		}
		if (item == null || item.isDisposed()) {
			return;
		}
		final int columnCount = table.getColumnCount();
		if (columnCount == 0 && column != 0) {
			return;
		}
		if (columnCount > 0 && (column < 0 || column >= columnCount)) {
			return;
		}

		boolean hadFocus = false;

		if (getEditor() == null || getEditor().isDisposed()) {
			return;
		}
		if (getEditor().getVisible())
		{
			hadFocus = getEditor().isFocusControl();
		} // this doesn't work because
		// resizing the column takes the focus away
		// before we get here
		getEditor().setBounds(computeEditorBounds());
		if (hadFocus)
		{
			if (getEditor() == null || getEditor().isDisposed()) {
				return;
			}
			getEditor().setFocus();
		}

	}

}
