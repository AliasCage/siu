/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Copyright (c) 2013, MPL CodeInside http://codeinside.ru
 */
package ru.codeinside.gses.lazyquerycontainer.test;

import java.util.Collection;
import java.util.Iterator;

import junit.framework.TestCase;

import ru.codeinside.gses.lazyquerycontainer.LazyQueryContainer;
import ru.codeinside.gses.lazyquerycontainer.LazyQueryDefinition;
import ru.codeinside.gses.lazyquerycontainer.LazyQueryView;
import ru.codeinside.gses.lazyquerycontainer.QueryItemStatus;
import ru.codeinside.gses.lazyquerycontainer.QueryView;

import com.vaadin.data.Container.ItemSetChangeEvent;
import com.vaadin.data.Container.ItemSetChangeListener;
import com.vaadin.data.Container.PropertySetChangeEvent;
import com.vaadin.data.Container.PropertySetChangeListener;
import com.vaadin.data.Item;
import com.vaadin.data.Property;

@SuppressWarnings("serial")
public class LazyQueryContainerTest extends TestCase implements ItemSetChangeListener, PropertySetChangeListener {

    private final int viewSize = 100;
    private LazyQueryContainer container;
    private boolean itemSetChangeOccurred = false;
    private boolean propertySetChangeOccurred = false;

    protected void setUp() throws Exception {
        super.setUp();

        LazyQueryDefinition definition = new LazyQueryDefinition(true, this.viewSize);
        definition.addProperty("Index", Integer.class, 0, true, true);
        definition.addProperty("Reverse Index", Integer.class, 0, true, false);
        definition.addProperty("Editable", String.class, "", false, false);
        definition.addProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS, QueryItemStatus.class, QueryItemStatus.None,
                true, false);

        MockQueryFactory factory = new MockQueryFactory(viewSize, 0, 0);
        QueryView view = new LazyQueryView(definition, factory);
        container = new LazyQueryContainer(view);
        container.addListener((ItemSetChangeListener) this);
        container.addListener((PropertySetChangeListener) this);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSize() {
        assertEquals(viewSize, container.size());
    }

    public void testGetItemIds() {
        Collection<?> ids = container.getItemIds();
        Iterator<?> idIterator = ids.iterator();
        for (int i = 0; i < viewSize; i++) {
            assertEquals(i, idIterator.next());
        }
    }
    
    public void testGetItem() {
        for (int i = 0; i < viewSize; i++) {
            Item item = container.getItem(i);
            Property indexProperty = item.getItemProperty("Index");
            assertEquals(i, indexProperty.getValue());
            assertTrue(indexProperty.isReadOnly());
        }
    }

    public void testAscendingSort() {
        container.sort(new Object[] { "Index" }, new boolean[] { true });

        for (int i = 0; i < viewSize; i++) {
            Item item = container.getItem(i);
            Property indexProperty = item.getItemProperty("Index");
            assertEquals(i, indexProperty.getValue());
            assertTrue(indexProperty.isReadOnly());
        }
    }

    public void testDescendingSort() {
        container.sort(new Object[] { "Index" }, new boolean[] { false });

        for (int i = 0; i < viewSize; i++) {
            Item item = container.getItem(i);
            Property indexProperty = item.getItemProperty("Index");
            assertEquals(viewSize - i - 1, indexProperty.getValue());
            assertTrue(indexProperty.isReadOnly());
        }
    }

    public void testGetSortablePropertyIds() {
        Collection<?> sortablePropertyIds = container.getSortableContainerPropertyIds();
        assertEquals(1, sortablePropertyIds.size());
        assertEquals("Index", sortablePropertyIds.iterator().next());
    }

    public void testItemSetChangeNotification() {
        container.refresh();
        assertTrue(itemSetChangeOccurred);
    }

    public void containerItemSetChange(ItemSetChangeEvent event) {
        itemSetChangeOccurred = true;
    }

    public void testPropertySetChangeNotification() {
        container.addContainerProperty("NewProperty", Integer.class, 1, true, true);
        assertTrue(propertySetChangeOccurred);
    }

    public void containerPropertySetChange(PropertySetChangeEvent event) {
        propertySetChangeOccurred = true;
    }

    public void testAddCommitItem() {
        int originalViewSize = container.size();
        assertFalse(container.isModified());
        int addIndex = (Integer) container.addItem();
        assertEquals("Item must be added at the beginning", addIndex, 0);
        assertEquals(originalViewSize + 1, container.size());
        assertEquals(QueryItemStatus.Added,
                container.getItem(addIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
        assertTrue(container.isModified());
        container.commit();
        assertFalse(container.isModified());
        assertEquals(QueryItemStatus.None,
                container.getItem(addIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
    }

    public void testAddTwiceCommitItem() {
        int originalViewSize = container.size();
        assertFalse(container.isModified());
        // Add the first Item
        int addIndex = (Integer) container.addItem();
        assertEquals("Item must be added at the beginning", addIndex, 0);
        assertEquals(originalViewSize + 1, container.size());
        assertEquals(QueryItemStatus.Added,
                container.getItem(addIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
        assertTrue(container.isModified());
        // Add a second Item
        addIndex = (Integer) container.addItem();
        assertEquals("Second item must be added first as well.", addIndex, 0);
        assertEquals(originalViewSize + 2, container.size());
        assertEquals(QueryItemStatus.Added,
                container.getItem(addIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
        assertTrue(container.isModified());
        container.commit();
        assertFalse(container.isModified());
        assertEquals(QueryItemStatus.None,
                container.getItem(addIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
    }

    public void testAddDiscardItem() {
        int originalViewSize = container.size();
        assertFalse(container.isModified());
        int addIndex = (Integer) container.addItem();
        assertEquals("Item must be added at the beginning", addIndex, 0);
        assertEquals(originalViewSize + 1, container.size());
        assertEquals(QueryItemStatus.Added,
                container.getItem(addIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
        assertTrue(container.isModified());
        container.discard();
        assertFalse(container.isModified());
        assertEquals(originalViewSize, container.size());
    }

    public void testModifyCommitItem() {
        int modifyIndex = 0;
        assertFalse(container.isModified());
        container.getItem(modifyIndex).getItemProperty("Editable").setValue("test");
        assertTrue(container.isModified());
        container.commit();
        assertFalse(container.isModified());
        assertEquals("test", container.getItem(modifyIndex).getItemProperty("Editable").getValue());
    }

    public void testModifyDiscardItem() {
        int modifyIndex = 0;
        assertFalse(container.isModified());
        container.getItem(modifyIndex).getItemProperty("Editable").setValue("test");
        assertTrue(container.isModified());
        container.discard();
        assertFalse(container.isModified());
        assertEquals("", container.getItem(modifyIndex).getItemProperty("Editable").getValue());
    }

    public void testRemoveCommitItem() {
        int removeIndex = 0;
        int originalViewSize = container.size();
        assertFalse(container.isModified());
        assertFalse(container.getItem(removeIndex).getItemProperty("Editable").isReadOnly());
        container.removeItem(removeIndex);
        assertEquals(originalViewSize, container.size());
        assertEquals(QueryItemStatus.Removed,
                container.getItem(removeIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
        assertTrue(container.getItem(removeIndex).getItemProperty("Editable").isReadOnly());
        assertTrue(container.isModified());
        container.commit();
        assertFalse(container.isModified());
        assertEquals(originalViewSize - 1, container.size());
        assertEquals(removeIndex + 1, container.getItem(removeIndex).getItemProperty("Index").getValue());
    }

    public void testRemoveDiscardItem() {
        int removeIndex = 0;
        int originalViewSize = container.size();
        assertFalse(container.isModified());
        assertFalse(container.getItem(removeIndex).getItemProperty("Editable").isReadOnly());
        container.removeItem(removeIndex);
        assertEquals(originalViewSize, container.size());
        assertEquals(QueryItemStatus.Removed,
                container.getItem(removeIndex).getItemProperty(LazyQueryView.PROPERTY_ID_ITEM_STATUS).getValue());
        assertTrue(container.getItem(removeIndex).getItemProperty("Editable").isReadOnly());
        assertTrue(container.isModified());
        container.discard();
        assertFalse(container.isModified());
        assertEquals(originalViewSize, container.size());
        assertEquals(removeIndex, container.getItem(removeIndex).getItemProperty("Index").getValue());
        assertFalse(container.getItem(removeIndex).getItemProperty("Editable").isReadOnly());
    }

}
