/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.server.api;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import org.opends.server.types.DN;


/**
 * The DITCacheMap class implements custom Map for structural
 * storage of arbitrary objects in Directory Information Tree
 * (DIT) like structure.
 *
 * This Map intended usage is for caching various server
 * objects which can be subject to subtree operations
 * like retrieval or removal of all objects under a
 * specific DN. While using a regular Map it would
 * require the entire Map iteration to achieve, this Map
 * implementation maintains such internal structure that
 * subtree operations are more efficient and do not
 * require iterations over the entire map, instead
 * additional subtree operations methods are provided by
 * this Map to do just that.
 *
 * API wise it behaves exactly like a regular Map
 * implementation except for providing additional
 * subtree methods. All required linkage and
 * structuring is performed within this Map
 * implementation itself and not exposed via the
 * API in any way. For example, putting these
 * key/value pairs
 *
 * cn=Object1,ou=Objects,dc=example,dc=com : object1
 * cn=Object2,ou=Objects,dc=example,dc=com : object2
 * cn=Object3,ou=Objects,dc=example,dc=com : object3
 *
 * then invoking a subtree method on this Map with
 * any of these keys
 *
 * ou=Objects,dc=example,dc=com
 * dc=example,dc=com
 * dc=com
 *
 * would bring all three objects previously stored in
 * this map into subtree operation scope. Standard
 * Map API methods can only work with the objects
 * previously stored in this map explicitly.
 *
 * Note that this Map implementation is not
 * synchronized.
 *
 * @param <T> arbitrary object type.
 */
public class DITCacheMap<T> extends AbstractMap<DN,T>
{
  /**
   * Node class for object storage and
   * linking to any subordinate nodes.
   * @param <T> arbitrary storage object.
   */
  private static final class Node<T>
  {
    // Node DN.
    DN dn;
    // Storage object or null if this node exist
    // only to support the DIT like structuring.
    T element;
    // Parent.
    Node<T> parent;
    // First child.
    Node<T> child;
    // Next sibling.
    Node<T> next;
    // Previous sibling.
    Node<T> previous;
  }

  // Map size reflecting only nodes
  // containing non empty elements.
  private int size = 0;

  // Backing Map implementation.
  private Map<DN,Node<T>> ditCacheMap;

  /**
   * Default constructor.
   */
  public DITCacheMap()
  {
    ditCacheMap = new HashMap<DN,Node<T>>();
  }

  /**
   * Constructs a new DITCacheMap from a given Map.
   * @param m existing Map to construct new
   *          DITCacheMap from.
   */
  public DITCacheMap(Map<? extends DN, ? extends T> m)
  {
    ditCacheMap = new HashMap<DN,Node<T>>();
    this.putAll(m);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return size;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEmpty()
  {
    return ditCacheMap.isEmpty();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean containsKey(Object key)
  {
    if (get((DN) key) != null)
    {
      return true;
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean containsValue(Object value)
  {
    for (Node<T> node : ditCacheMap.values())
    {
      if ((node.element != null) &&
           node.element.equals(value))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T get(Object key)
  {
    Node<T> node = ditCacheMap.get((DN)key);
    if (node != null)
    {
      return node.element;
    }
    return null;
  }

  /**
   * Returns a set of stored objects
   * subordinate to subtree DN.
   * @param key subtree DN.
   * @return collection of stored objects
   *         subordinate to subtree DN.
   */
  public Collection<T> getSubtree(DN key)
  {
    return new DITSubtreeSet(key);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T put(DN key, T value)
  {
    T returnValue = null;

    Node<T> existingNode = ditCacheMap.get(key);
    if (existingNode != null)
    {
      returnValue = existingNode.element;
      existingNode.element = value;
    }
    else
    {
      Node<T> node = new Node<T>();
      node.dn = key;
      node.element = value;
      node.parent = null;
      node.child = null;
      node.next = null;
      node.previous = null;

      ditCacheMap.put(key, node);
      size++;

      for (DN parentDN = key.getParent();
        parentDN != null;
        parentDN = parentDN.getParent())
      {
        Node<T> parentNode = ditCacheMap.get(parentDN);
        if (parentNode != null)
        {
          if (parentNode.child != null)
          {
            Node<T> lastNode = parentNode.child;
            while (lastNode.next != null)
            {
              lastNode = lastNode.next;
            }
            node.previous = lastNode;
            lastNode.next = node;
          }
          else
          {
            parentNode.child = node;
          }
          node.parent = parentNode;
          break;
        }
        else
        {
          parentNode = new Node<T>();
          parentNode.dn = parentDN;
          parentNode.element = null;
          parentNode.parent = null;
          parentNode.child = node;
          parentNode.next = null;
          parentNode.previous = null;
          ditCacheMap.put(parentDN, parentNode);
          node.parent = parentNode;
          node = parentNode;
        }
      }
    }

    return returnValue;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T remove(Object key)
  {
    T returnValue = null;

    Node<T> existingNode = ditCacheMap.get((DN)key);
    if ((existingNode != null) &&
        (existingNode.element != null))
    {
      returnValue = existingNode.element;

      try
      {
        if (existingNode.child == null)
        {
          ditCacheMap.remove((DN)key);
        }
        else
        {
          existingNode.element = null;
          return returnValue;
        }
      }
      finally
      {
        size--;
      }

      for (DN parentDN = existingNode.dn.getParent();
        parentDN != null;
        parentDN = parentDN.getParent())
      {
        Node<T> parentNode = ditCacheMap.get(parentDN);
        if (parentNode.child == existingNode)
        {
          parentNode.child = existingNode.next;
        }
        else
        {
          if (existingNode.next != null)
          {
            existingNode.next.previous = existingNode.previous;
          }
          if (existingNode.previous != null)
          {
            existingNode.previous.next = existingNode.next;
          }
        }
        if ((parentNode.child == null) &&
            (parentNode.element == null))
        {
          existingNode = ditCacheMap.remove(parentDN);
        }
        else
        {
          break;
        }
      }
    }

    return returnValue;
  }

  /**
   * Removes a set of stored objects subordinate to subtree DN.
   * @param key subtree DN.
   * @param values collection for removed objects subordinate
   *               to subtree DN or <code>null</code>.
   * @return <code>true</code> on success or
   *         <code>false</code> otherwise.
   */
  public boolean removeSubtree(DN key, Collection<? super T> values)
  {
    Node<T> rootNode = ditCacheMap.get(key);

    if (rootNode != null)
    {
      if (rootNode.element != null)
      {
        remove(key);
        if (values != null)
        {
          values.add(rootNode.element);
        }
      }

      Node<T> node = rootNode.child;

      while (node != null)
      {
        if (node.element != null)
        {
          remove(node.dn);
          if (values != null)
          {
            values.add(node.element);
          }
        }
        if (node.child != null)
        {
          node = node.child;
        }
        else
        {
          while ((node.next == null) &&
                 (node.parent != rootNode))
          {
            node = node.parent;
          }
          node = node.next;
        }
      }
      return true;
    }

    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void putAll(Map<? extends DN, ? extends T> m)
  {
    for (Entry<? extends DN, ? extends T> entry : m.entrySet())
    {
      put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clear()
  {
    ditCacheMap.clear();
    size = 0;
  }

  /**
   * {@inheritDoc}
   */
  public Set<Entry<DN, T>> entrySet()
  {
    return new DITCacheEntrySet();
  }

  /**
   * EntrySet class implementation for the DITCacheMap.
   */
  private class DITCacheEntrySet extends AbstractSet<Entry<DN, T>>
  {
    /**
     * Iterator class implementation for the DITCacheEntrySet.
     */
    private class EntryIterator implements Iterator<Entry<DN, T>>
    {
      private Iterator<Entry<DN, Node<T>>> ditCacheMapIterator;
      private Entry<DN, Node<T>> currentEntry;
      private Entry<DN, Node<T>> nextEntry;
      private boolean hasNext;

      /**
       * Default constructor.
       */
      public EntryIterator()
      {
        ditCacheMapIterator = ditCacheMap.entrySet().iterator();
        currentEntry = null;
        nextEntry = null;
        hasNext = false;
      }

      /**
       * {@inheritDoc}
       */
      public boolean hasNext()
      {
        if (hasNext)
        {
          return true;
        }
        while (ditCacheMapIterator.hasNext())
        {
          Entry<DN, Node<T>> entry = ditCacheMapIterator.next();
          Node<T> node = entry.getValue();
          if ((node != null) && (node.element != null))
          {
            nextEntry = entry;
            hasNext = true;
            return true;
          }
        }
        nextEntry = null;
        return false;
      }

      /**
       * {@inheritDoc}
       */
      public Entry<DN, T> next()
      {
        if (nextEntry != null)
        {
          Node<T> node = nextEntry.getValue();
          currentEntry = nextEntry;
          nextEntry = null;
          hasNext = false;
          return new DITCacheMapEntry(node.dn, node.element);
        }
        while (ditCacheMapIterator.hasNext())
        {
          Entry<DN, Node<T>> entry = ditCacheMapIterator.next();
          Node<T> node = entry.getValue();
          if ((node != null) && (node.element != null))
          {
            currentEntry = entry;
            hasNext = false;
            return new DITCacheMapEntry(node.dn, node.element);
          }
        }
        throw new NoSuchElementException();
      }

      /**
       * {@inheritDoc}
       */
      public void remove()
      {
        if (currentEntry != null)
        {
          Entry<DN, Node<T>> oldIteratorEntry = null;
          if (hasNext())
          {
            oldIteratorEntry = nextEntry;
          }
          if (DITCacheMap.this.remove(currentEntry.getKey()) != null)
          {
            ditCacheMapIterator = ditCacheMap.entrySet().iterator();
            currentEntry = null;
            nextEntry = null;
            hasNext = false;
            while (hasNext())
            {
              Entry<DN, T> newIteratorEntry = next();
              if ((oldIteratorEntry != null) &&
                   oldIteratorEntry.getKey().equals(
                   newIteratorEntry.getKey()) &&
                   oldIteratorEntry.getValue().element.equals(
                   newIteratorEntry.getValue()))
              {
                nextEntry = currentEntry;
                hasNext = true;
                return;
              }
            }
            currentEntry = null;
            nextEntry = null;
            hasNext = false;
            return;
          }
        }
        throw new IllegalStateException();
      }
    }

    /**
     * {@inheritDoc}
     */
    public int size()
    {
      return DITCacheMap.this.size();
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<Entry<DN, T>> iterator()
    {
      return new EntryIterator();
    }
  }

  /**
   * Map.Entry class implementation for the DITCacheMap.
   */
  private class DITCacheMapEntry implements Map.Entry<DN, T>
  {
    private DN key;
    private T  value;

    /**
     * Constructs a new DITCacheMapEntry
     * with given key and value.
     * @param key Map.Entry key.
     * @param value Map.Entry value.
     */
    public DITCacheMapEntry(DN key, T value)
    {
      this.key = key;
      this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    public DN getKey()
    {
      return key;
    }

    /**
     * {@inheritDoc}
     */
    public T getValue()
    {
      return value;
    }

    /**
     * {@inheritDoc}
     */
    public T setValue(T value)
    {
      Node<T> node = ditCacheMap.get(key);
      T oldValue = this.value;
      node.element = value;
      this.value = value;
      return oldValue;
    }
  }

  /**
   * SubtreeSet class implementation.
   */
  private class DITSubtreeSet extends AbstractSet<T>
  {
    // Set key.
    private DN key;

    /**
     * Default constructor.
     */
    public DITSubtreeSet()
    {
      this.key = null;
    }

    /**
     * Keyed constructor.
     * @param key to construct
     *        this set from.
     */
    public DITSubtreeSet(DN key)
    {
      this.key = key;
    }

    /**
     * Iterator class implementation for SubtreeSet.
     */
    private class SubtreeSetIterator implements Iterator<T>
    {
      // Iterator key.
      private DN key;

      // Iterator root node.
      private Node<T> rootNode;

      // Iterator current node.
      private Node<T> node;

      /**
       * Default constructor.
       */
      public SubtreeSetIterator()
      {
        this.key = DITSubtreeSet.this.key;
        rootNode = ditCacheMap.get(this.key);
        node = rootNode;
      }

      /**
       * Keyed constructor.
       * @param key to cue this
       *        iterator from.
       */
      public SubtreeSetIterator(DN key)
      {
        this.key = key;
        rootNode = ditCacheMap.get(this.key);
        node = rootNode;
      }

      /**
       * {@inheritDoc}
       */
      public boolean hasNext()
      {
        if (rootNode != null)
        {
          if (node == rootNode)
          {
            if (rootNode.element != null)
            {
              return true;
            }
          }
          while (node != null)
          {
            if (node.element != null)
            {
              return true;
            }
            if (node.child != null)
            {
              node = node.child;
            }
            else
            {
              while ((node.next == null) &&
                     (node.parent != rootNode))
              {
                node = node.parent;
              }
              node = node.next;
            }
          }
        }
        return false;
      }

      /**
       * {@inheritDoc}
       */
      public T next()
      {
        T element = null;

        if (rootNode != null)
        {
          if (node == rootNode)
          {
            node = rootNode.child;
            if (rootNode.element != null)
            {
              return rootNode.element;
            }
          }
          while (node != null)
          {
            if (node.element != null)
            {
              element = node.element;
            }
            else
            {
              element = null;
            }
            if (node.child != null)
            {
              node = node.child;
            }
            else
            {
              while ((node.next == null) &&
                     (node.parent != rootNode))
              {
                node = node.parent;
              }
              node = node.next;
            }
            if (element != null)
            {
              return element;
            }
          }
        }
        throw new NoSuchElementException();
      }

      /**
       * {@inheritDoc}
       */
      public void remove()
      {
        throw new UnsupportedOperationException();
      }
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<T> iterator()
    {
      return new SubtreeSetIterator();
    }

    /**
     * {@inheritDoc}
     */
    public int size()
    {
      int size = 0;

      Iterator<T> iterator = new SubtreeSetIterator(this.key);
      while (iterator.hasNext())
      {
        iterator.next();
        size++;
      }

      return size;
    }
  }
}
