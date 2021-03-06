/*
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License:
 *
 * http://creativecommons.org/licenses/by-nc/3.0/
 *
 * For alternative conditions contact the author.
 *
 * Copyright (c) 2011 "Robin Wenglewski <robin@wenglewski.de>"
 */
package de.rwhq.btree;

import de.rwhq.btree.AdjustmentAction.ACTION;
import de.rwhq.io.rm.*;
import de.rwhq.serializer.FixLengthSerializer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;


/**
 * autosaves
 *
 * @param <K>
 * @param <V>
 */
class LeafNode<K, V> implements Node<K, V>, ComplexPage {

	private static final Log LOG = LogFactory.getLog(LeafNode.class);

	static enum Header {
		NODE_TYPE(0), // char
		NUMBER_OF_KEYS(Character.SIZE / 8), // int
		NEXT_LEAF_ID((Character.SIZE + Integer.SIZE) / 8); // int


		private int offset;

		Header(final int offset) {
			this.offset = offset;
		}

		static int size() {
			return (Character.SIZE + 2 * Integer.SIZE) / 8;
		} // 6

		int getOffset() {
			return offset;
		}
	}

	/**
	 * If a leaf page is less full than this factor, it may be target of operations where entries are moved from one page
	 * to another.
	 */
	private static final float MAX_LEAF_ENTRY_FILL_LEVEL_TO_MOVE = 0.75f;

	private static final BTree.NodeType NODE_TYPE = BTree.NodeType.LEAF_NODE;

	private final FixLengthSerializer<V, byte[]> valueSerializer;

	private final Comparator<K> comparator;

	private boolean valid = false;

	// right now, we always store key/value pairs. If the entries are not unique,
	// it could make sense to store the key once with references to all values
	//TODO: investigate if we should do this
	private int maxEntries;


	private static final   int     NOT_FOUND    = -1;
	protected static final Integer NO_NEXT_LEAF = 0;

	private final RawPage rawPage;

	// counters
	private int numberOfEntries = 0;

	private final PageManager<LeafNode<K, V>> leafPageManager;

	private final FixLengthSerializer<K, byte[]> keySerializer;


	LeafNode(
			final RawPage page,
			final FixLengthSerializer<K, byte[]> keySerializer,
			final FixLengthSerializer<V, byte[]> valueSerializer,
			final Comparator<K> comparator,
			final PageManager<LeafNode<K, V>> leafPageManager,
			final int minNumberOfValues
	) {
		this.leafPageManager = leafPageManager;
		this.rawPage = page;
		this.keySerializer = keySerializer;
		this.valueSerializer = valueSerializer;
		this.comparator = comparator;
		
		// one pointer to key, one to value
		maxEntries = (rawPage.bufferForReading(0).limit() - Header.size()) /
				(valueSerializer.getSerializedLength() + keySerializer.getSerializedLength());

		final int requiredBytes = Header.size() + minNumberOfValues *
				(keySerializer.getSerializedLength() + valueSerializer.getSerializedLength());
		if (page.bufferForReading(0).limit() - requiredBytes < 0)
			throw new IllegalArgumentException("The RawPage must have space for at least " +
					minNumberOfValues + " Entries (" + requiredBytes + " bytes)");
	}

	/**
	 * If a leaf page has at least that many free slots left, we can move pointers to it from another node. This number is
	 * computed from the <tt>MAX_LEAF_ENTRY_FILL_LEVEL_TO_MOVE</tt> constant.
	 * @return
	 */
	private int getMinFreeLeafEntriesToMove() {
		return (int) (getMaximalNumberOfEntries() *
				(1 - MAX_LEAF_ENTRY_FILL_LEVEL_TO_MOVE)) + 2;
	}

	public boolean isFull() {
		return numberOfEntries == maxEntries;
	}


	private void ensureValid() {
		if (isValid())
			return;

		System.err.println("The current LeafPage with the id " + rawPage.id() + " is not valid");
		System.exit(1);
	}

	public String toString() {
		String str = "leafNode(id: " + getId() + ", values: " + getNumberOfEntries() + "): ";
		str += "firstKey: " + (getFirstLeafKey() == null ? "null" : getFirstLeafKey().toString());
		str += ",lastKey: " + (getLastLeafKey() == null ? "null" : getLastLeafKey().toString());
		return str;
	}

	public void prependEntriesFromOtherPage(final LeafNode<K, V> source, final int num) {
		// checks
		if (num < 0)
			throw new IllegalArgumentException("num must be > 0");

		if (num > source.getNumberOfEntries())
			throw new IllegalArgumentException("the source leaf has not enough entries");

		if (getNumberOfEntries() + num > maxEntries)
			throw new IllegalArgumentException(
					"not enough space in this leaf to prepend " + num + " entries from other leaf");

		if (getNumberOfEntries() > 0 && comparator.compare(source.getLastLeafKey(), getFirstLeafKey()) > 0) {
			throw new IllegalStateException(
					"the last key of the provided source leaf is larger than this leafs first key");
		}

		final ByteBuffer buffer = rawPage().bufferForWriting(0);

		// make space in this leaf, move all elements to the right
		final int totalSize = num * (keySerializer.getSerializedLength() + valueSerializer.getSerializedLength());
		final int byteToMove = buffer.limit() - Header.size() - totalSize;
		System.arraycopy(buffer.array(), Header.size(), buffer.array(), Header.size() + totalSize, byteToMove);

		// copy from other to us
		final int sourceOffset = source.getOffsetForKeyPos(source.getNumberOfEntries() - num);
		System.arraycopy(source.rawPage().bufferForWriting(0).array(), sourceOffset, buffer.array(), Header.size(),
				totalSize);

		// update headers, also sets modified
		source.setNumberOfEntries(source.getNumberOfEntries() - num);
		setNumberOfEntries(getNumberOfEntries() + num);
	}

	private void setNumberOfEntries(final int num) {
		numberOfEntries = num;
		rawPage().bufferForWriting(Header.NUMBER_OF_KEYS.getOffset()).putInt(numberOfEntries);
	}

	public K getLastLeafKey() {
		if (getNumberOfEntries() == 0)
			return null;

		int offset = offsetBehindLastEntry();
		offset -= keySerializer.getSerializedLength() + valueSerializer.getSerializedLength();
		return getKeyAtOffset(offset);
	}

	@Override public byte[] getLastLeafKeySerialized() {
		final ByteBuffer buffer = rawPage().bufferForReading(getOffsetForKeyPos(getNumberOfEntries() - 1));
		final byte[] buf = new byte[keySerializer.getSerializedLength()];
		buffer.get(buf);
		return buf;
	}

	public K getKeyAtOffset(final int offset) {
		final byte[] bytes = new byte[keySerializer.getSerializedLength()];
		rawPage().bufferForReading(offset).get(bytes);
		return keySerializer.deserialize(bytes);
	}

	public K getFirstLeafKey() {
		if (getNumberOfEntries() == 0)
			return null;

		final int pos = getOffsetForKeyPos(0);
		return getKeyAtOffset(pos);
	}


	/**
	 *
	 * adds an entry to this LeafNodes rawPage, does not sync!
	 *
	 * @param key
	 * @param value
	 */
	private void addEntry(final K key, final V value) {

		final ByteBuffer buf = rawPage().bufferForWriting(0);
		int offset = offsetOfKey(key, true);

		if (offset == -1) {
			offset = offsetBehindLastEntry();
		} else {
			// move everything including pos backwards
			System.arraycopy(buf.array(), offset, buf.array(), offset + keySerializer.getSerializedLength()
					+ valueSerializer.getSerializedLength(),
					buf.capacity() - (offset + keySerializer.getSerializedLength() + valueSerializer.getSerializedLength()));
		}
		// insert both
		buf.position(offset);
		buf.put(keySerializer.serialize(key));
		buf.put(valueSerializer.serialize(value));

		setNumberOfEntries(getNumberOfEntries() + 1);
	}

	public int getNumberOfEntries() {
		return numberOfEntries;
	}

	/* (non-Javadoc)
		  * @see MultiMap#containsKey(java.lang.Object)
		  */
	@Override
	public boolean containsKey(final K key) {
		return offsetOfKey(key) != NOT_FOUND;
	}

	/**
	 * @param pos,
	 * 		must be between 0 and numberOfEntries - 1
	 * @return offset
	 */
	int getOffsetForKeyPos(final int pos) {
		if (pos < 0 || pos >= getNumberOfEntries())
			throw new IllegalArgumentException(
					"invalid pos: " + pos + ". pos must be between 0 and numberOfEntries - 1");

		return Header.size() + pos * (valueSerializer.getSerializedLength() + keySerializer.getSerializedLength());
	}

	private int offsetForValuePos(final int i) {
		return getOffsetForKeyPos(i) + valueSerializer.getSerializedLength();
	}

	/**
	 * does not alter the header bytebuffer
	 *
	 * @return
	 */
	private int offsetBehindLastEntry() {
		return Header.size() + getNumberOfEntries() * (valueSerializer.getSerializedLength() + keySerializer.getSerializedLength());
	}

	/* (non-Javadoc)
		  * @see com.rwhq.multimap.MultiMap#get(java.lang.Object)
		  */
	@Override
	public List<V> get(final K key) {
		final List<V> result = new ArrayList<V>();


		final byte[] keyBuf = keySerializer.serialize(key);

		final byte[] tmpKeyBuf = new byte[keySerializer.getSerializedLength()];
		final byte[] tmpValBuf = new byte[valueSerializer.getSerializedLength()];

		final int pos = offsetOfKey(key);
		if (pos == NOT_FOUND)
			return result;


		final ByteBuffer buffer = rawPage().bufferForReading(pos);


		while (buffer.position() < offsetBehindLastEntry()) {
			buffer.get(tmpKeyBuf);
			if (Arrays.equals(tmpKeyBuf, keyBuf)) {
				buffer.get(tmpValBuf);
				result.add(valueSerializer.deserialize(tmpValBuf));
			}
		}

		return result;
	}

	/**
	 * @param key
	 * @param takeNext
	 * 		boolean, whether if the key was not found the next higher key should be taken
	 * @return position to set the buffer to, where the key starts, -1 if key not found
	 */
	private int offsetOfKey(final K key, final boolean takeNext) {

		final byte[] pointerBuf = new byte[valueSerializer.getSerializedLength()];
		final byte[] keyBuf = new byte[keySerializer.getSerializedLength()];

		final ByteBuffer buffer = rawPage().bufferForReading(Header.size());

		for (int i = 0; i < getNumberOfEntries(); i++) {

			buffer.get(keyBuf);

			final int compResult = comparator.compare(keySerializer.deserialize(keyBuf), key);

			if (compResult == 0) {
				return buffer.position() - keySerializer.getSerializedLength();
			} else if (compResult > 0) {
				if (takeNext)
					return buffer.position() - keySerializer.getSerializedLength();
				else
					return NOT_FOUND;
			}

			// if compresult < 0:
			// get the data pointer but do nothing with it
			buffer.get(pointerBuf);
		}
		return NOT_FOUND;
	}

	private int offsetOfKey(final K key) {
		return offsetOfKey(key, false);
	}


	/**
	 * @param currentPos
	 * @return a valid position to read the next key from, or NOT_FOUND
	 */
	private int getPosWhereNextKeyStarts(int currentPos) {
		if (currentPos < Header.size())
			currentPos = Header.size();

		currentPos -= Header.size();
		currentPos /= (valueSerializer.getSerializedLength() + keySerializer.getSerializedLength());
		if (currentPos >= getNumberOfEntries())
			return NOT_FOUND;

		currentPos *= (valueSerializer.getSerializedLength() + keySerializer.getSerializedLength());
		return currentPos + Header.size();
	}

	/* (non-Javadoc)
		  * @see MultiMap#remove(java.lang.Object)
		  */
	@Override
	public int remove(final K key) {
		
		final int pos = offsetOfKey(key);
		if (pos == NOT_FOUND)
			return 0;

		final int numberOfValues = get(key).size();
		final int sizeOfValues =
				numberOfValues * (valueSerializer.getSerializedLength() + keySerializer.getSerializedLength());

		//TODO: free key and value pages

		// shift the pointers after key
		final ByteBuffer buffer = rawPage().bufferForWriting(0);
		System.arraycopy(buffer.array(), pos + sizeOfValues, buffer.array(), pos,
				buffer.capacity() - pos - sizeOfValues);
		setNumberOfEntries(getNumberOfEntries() - numberOfValues);

		rawPage().sync();

		return numberOfValues;
	}

	/* (non-Javadoc)
		  * @see Node#remove(java.lang.Object, java.lang.Object)
		  */
	@Override
	public int remove(final K key, final V value) {
		final int offset = offsetOfKey(key);
		if (offset == NOT_FOUND)
			return 0;


		final int numberOfValues = get(key).size();

		final ByteBuffer buffer = rawPage().bufferForWriting(offset);
		final byte[] buf1 = new byte[keySerializer.getSerializedLength()];
		final byte[] buf2 = new byte[valueSerializer.getSerializedLength()];
		int removed = 0;

		for (int i = 0; i < numberOfValues; i++) {
			buffer.get(buf1);
			buffer.get(buf2); // load only the value
			final V val = valueSerializer.deserialize(buf2);

			if (val == null)
				throw new IllegalStateException("value retrieved from a value page should not be null");

			// we cant use a comparator here since we have none for values (its the only case we need it)
			if (val.equals(value)) {
				// also free key page
				// move pointers forward and reset buffer
				final int startingPos = buffer.position() - buf1.length - buf2.length;
				System.arraycopy(buffer.array(), buffer.position(), buffer.array(), startingPos,
						buffer.capacity() - buffer.position());

				buffer.position(startingPos);

				removed++;
			}
		}

		setNumberOfEntries(getNumberOfEntries() - removed);
		return removed;
	}

	/**
	 * @param key
	 * @return
	 */
	private DataPage<V> getValueDataPage(final K key) {
		// TODO Auto-generated method stub
		return null;
	}


	/**
	 * @param key
	 * @return
	 */
	private DataPage<K> getKeyDataPage(final K key) {
		// TODO Auto-generated method stub
		return null;
	}


	/* (non-Javadoc)
		  * @see Node#destroy()
		  */
	@Override
	public void destroy() {
		leafPageManager.removePage(rawPage().id());
	}

	/* (non-Javadoc)
		  * @see ComplexPage#initialize()
		  */
	@Override
	public void initialize() {
		rawPage.bufferForWriting(Header.NODE_TYPE.getOffset()).putChar(NODE_TYPE.serialize());
		setNumberOfEntries(0);
		setNextLeafId(NO_NEXT_LEAF);
		valid = true;
		
		rawPage.sync();
	}


	/** @param kvs
	 * @see #bulkInitialize(java.util.AbstractMap.SimpleEntry[], int, int) with from = 0
	 * @return*/
	public int bulkInitialize(final SimpleEntry<K, V>[] kvs) {
		return bulkInitialize(kvs, 0, kvs.length - 1);
	}


	/**
	 * Initializes the Leaf with data
	 *
	 * @param kvs
	 * 		data to insert as KeyValueObj Array
	 * @param from
	 * 		from where in the array to start inserting
	 * 	@param maxTo
	 * 	    usually lvs.length - 1
	 * @return number of keys inserted
	 */
	public int bulkInitialize(final SimpleEntry<K, ?>[] kvs, final int from, final int maxTo) {
		initialize();

		final int remainingToInsert = maxTo - from + 1;
		if (remainingToInsert <= 0)
			return 0;

		final ByteBuffer buf = rawPage().bufferForWriting(Header.size());

		final int entrySize = keySerializer.getSerializedLength() + valueSerializer.getSerializedLength();
		final int entriesThatFit = buf.remaining() / entrySize;
		final int entriesToInsert = entriesThatFit > remainingToInsert ? remainingToInsert : entriesThatFit;

		// determine value type
		boolean isSerialized = (kvs[from].getValue() instanceof byte[]);



		if(!isSerialized){
			for (int i = 0; i < entriesToInsert; i++) {
				buf.put(keySerializer.serialize(kvs[from + i].getKey()));
				buf.put(valueSerializer.serialize((V) kvs[from + i].getValue()));
			}
		} else {
			for (int i = 0; i < entriesToInsert; i++) {
				buf.put(keySerializer.serialize(kvs[from + i].getKey()));
				buf.put((byte[]) kvs[from + i].getValue());
			}
		}

		setNumberOfEntries(entriesToInsert);

		rawPage.sync();
		return entriesToInsert;
	}


	/* (non-Javadoc)
		  * @see ComplexPage#load()
		  */
	@Override
	public void load() {
		final ByteBuffer buf = rawPage().bufferForReading(0);
		if (buf.getChar() != NODE_TYPE.serialize())
			throw new IllegalStateException("The RawPage " + rawPage.id() + " doesnt have the Leaf Node Type");

		numberOfEntries = rawPage().bufferForReading(Header.NUMBER_OF_KEYS.getOffset()).getInt();
		valid = true;
	}


	/* (non-Javadoc)
		  * @see ComplexPage#isValid()
		  */
	@Override
	public boolean isValid() {
		return valid;
	}

	@Override
	public void loadOrInitialize() throws IOException {
		try {
			load();
		} catch (Exception e) {
			initialize();
		}
	}


	/* (non-Javadoc)
		  * @see ComplexPage#rawPage()
		  */
	@Override
	public RawPage rawPage() {
		return rawPage;
	}

	/* (non-Javadoc)
		  * @see com.rwhq.btree.Node#insert(java.lang.Object, java.lang.Object)
		  */
	@Override
	public AdjustmentAction<K, V> insert(final K key, final V value) {
		ensureValid();

		if (!isFull()) {
			// serialize data
			addEntry(key, value);
			rawPage().sync();
			return null;
		}

		// if leaf does not have enough space but we can move some data to the next leaf
		if (hasNextLeaf()) {
			final LeafNode<K, V> nextLeaf = leafPageManager.getPage(this.getNextLeafId());

			if (nextLeaf.getRemainingEntries() >= getMinFreeLeafEntriesToMove()) {
				nextLeaf.prependEntriesFromOtherPage(this, nextLeaf.getRemainingEntries() >> 1);

				// see on which page we will insert the value
				if (comparator.compare(key, this.getLastLeafKey()) > 0) {
					nextLeaf.insert(key, value);
				} else {
					this.insert(key, value);
				}

				rawPage.sync();
				nextLeaf.rawPage.sync();
				
				return new AdjustmentAction<K, V>(ACTION.UPDATE_KEY, nextLeaf.getFirstLeafKeySerialized(), null);
			}


		}

		// allocate new leaf
		final LeafNode<K, V> newLeaf = leafPageManager.createPage();
		newLeaf.setNextLeafId(getNextLeafId());
		setNextLeafId(newLeaf.getId());

		// newLeaf.setLastKeyContinuesOnNextPage(root.isLastKeyContinuingOnNextPage());

		// move half of the keys to new page
		newLeaf.prependEntriesFromOtherPage(this,
				this.getNumberOfEntries() >> 1);

		// see on which page we will insert the value
		if (comparator.compare(key, this.getLastLeafKey()) > 0) {
			newLeaf.insert(key, value);
		} else {
			this.insert(key, value);
		}

		rawPage.sync();
		newLeaf.rawPage.sync();

		// just to make sure, that the adjustment action is correct:
		final AdjustmentAction<K, V> action = new AdjustmentAction<K, V>(ACTION.INSERT_NEW_NODE,
				newLeaf.getFirstLeafKeySerialized(), newLeaf.rawPage().id());

		return action;
	}


	/** @return id of the next leaf or null */
	public Integer getNextLeafId() {
		final ByteBuffer buffer = rawPage().bufferForReading(Header.NEXT_LEAF_ID.getOffset());
		final Integer result = buffer.getInt();
		return result == 0 ? null : result;
	}

	public void setNextLeafId(final Integer id) {
		final ByteBuffer buffer = rawPage().bufferForWriting(Header.NEXT_LEAF_ID.getOffset());
		buffer.putInt(id == null ? NO_NEXT_LEAF : id);
		rawPage().sync();
	}

	public boolean hasNextLeaf() {
		return getNextLeafId() != null;
	}

	/* (non-Javadoc)
		  * @see com.rwhq.btree.Node#getKeyPointer(int)
		  */
	@Override
	public PagePointer getKeyPointer(final int pos) {

		if (pos >= 0) {
			getOffsetForKeyPos(pos);
		}

		return null;
	}

	/* (non-Javadoc)
		  * @see com.rwhq.btree.Node#getId()
		  */
	@Override
	public Integer getId() {
		return rawPage.id();
	}

	public int getRemainingEntries() {
		return getMaximalNumberOfEntries() - getNumberOfEntries();
	}

	/** @return the maximal number of Entries */
	public int getMaximalNumberOfEntries() {
		return maxEntries;
	}

	/* (non-Javadoc)
		  * @see Node#getNumberOfUniqueKeys()
		  */
	@Override
	public int getNumberOfKeys() {
		throw new UnsupportedOperationException();
	}

	/**
	 * @param pos,
	 * 		starting with 0, going to numberOfEntries - 1
	 * @return
	 */
	public K getKeyAtPosition(final int pos) {
		return getKeyAtOffset(getOffsetForKeyPos(pos));
	}

	public List<K> getKeySet() {
		final List<K> result = new ArrayList<K>();
		for (int i = 0; i < getNumberOfEntries(); i++) {
			result.add(getKeyAtPosition(i));
		}
		return result;
	}

	class KeyStruct {
		private int position = 0;

		private KeyStruct() {
		}

		private KeyStruct(final int position) {
			this.position = position;
		}

		private K getKey() {
			final byte[] bytes = new byte[keySerializer.getSerializedLength()];
			rawPage.bufferForReading(getOffset()).get(bytes);
			return keySerializer.deserialize(bytes);
		}

		private int getOffset() {
			return Header.size() + position * (keySerializer.getSerializedLength() + valueSerializer.getSerializedLength());
		}

		/** @return true if next Key exists */
		public boolean becomeNext() {
			position++;
			return position < getNumberOfEntries();
		}

		private int getValueOffset() {
			return getOffset() + keySerializer.getSerializedLength();
		}

		public V getValue() {
			final byte[] bytes = new byte[valueSerializer.getSerializedLength()];
			rawPage().bufferForReading(getValueOffset()).get(bytes);
			return valueSerializer.deserialize(bytes);
		}
	}


	public class LeafNodeIterator implements Iterator<V> {

		private final K              from;
		private final K              to;
		private       V              next;
		private       KeyStruct      currentKeyStruct;

		public LeafNodeIterator(final K from, final K to) {
			this.from = from;
			this.to = to;

			if(from != null)
				currentKeyStruct = firstKeyStructEqualOrLargerThan(from);
			else
				currentKeyStruct = new KeyStruct();
			
			if(currentKeyStruct == null)
				LOG.warn("iterator requested from a key that is larger than this leafs largest key");
		}

		/* (non-Javadoc)
				* @see java.util.Iterator#hasNext()
				*/
		@Override
		public boolean hasNext() {
			if (next != null)
				return true;

			next = next();
			return next != null;
		}

		/* (non-Javadoc)
				* @see java.util.Iterator#next()
				*/
		@Override
		public V next() {

			if (next != null) {
				final V result = next;
				next = null;
				return result;
			}

			// from key was larger than this leaf
			if(currentKeyStruct == null)
				return null;

			// if we are at the end of the leaf, or the current key is larger than the to we were looking for
			if(currentKeyStruct.position >= getNumberOfEntries() || (to != null && comparator.compare(currentKeyStruct.getKey(), to) > 0)){
				return null;
			}
			
			final V result = currentKeyStruct.getValue();
			currentKeyStruct.becomeNext();
			return result;
		}

		@Override public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	private KeyStruct firstKeyStructEqualOrLargerThan(final K from) {
		final KeyStruct current = new KeyStruct(0);
		do {
			if (comparator.compare(current.getKey(), from) >= 0) {
				return current;
			}
		} while (current.becomeNext());

		return null;
	}


	/* (non-Javadoc)
			  * @see com.rwhq.multimap.btree.Node#getIterator(java.lang.Object, java.lang.Object)
			  */
	@Override
	public Iterator<V> getIterator(final K from, final K to) {
		return new LeafNodeIterator(from, to);
	}

	@Override public int getDepth() {
		return 1;
	}

	@Override public void checkStructure() throws IllegalStateException {
		K lastKey = null;
		for (int i = 0; i < getNumberOfEntries(); i++) {
			if (lastKey != null && comparator.compare(lastKey, getKeyAtPosition(i)) > 0)
				throw new IllegalStateException("lastKey should be smaller or equal to current key");
			lastKey = getKeyAtPosition(i);
		}
	}

	/* (non-Javadoc)
		* @see com.rwhq.btree.Node#getFirst(java.lang.Object)
		*/
	@Override
	public V getFirst
	(final K
			 key) {
		final List<V> res = get(key);
		return res.size() > 0 ? res.get(0) : null;
	}

	@Override public byte[] getFirstLeafKeySerialized
			() {
		if (getNumberOfEntries() == 0)
			throw new IllegalStateException("you must have keys to get the first serialized key");

		final byte[] result = new byte[keySerializer.getSerializedLength()];
		rawPage().bufferForReading(Header.size()).get(result);
		return result;
	}
}
