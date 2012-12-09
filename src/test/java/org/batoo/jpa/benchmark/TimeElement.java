/*
 * Copyright (c) 2012 - Batoo Software ve Consultancy Ltd.
 * 
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.batoo.jpa.benchmark;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableLong;

import com.google.common.collect.Lists;

/**
 * 
 * @author hceylan
 * @since 2.0.0
 */
public class TimeElement extends HashMap<String, TimeElement> implements Comparable<TimeElement> {

	enum TimeType {
		JPA,
		JDBC,
		DB
	}

	private static final long serialVersionUID = 1L;

	private long self;
	private final String key;
	private volatile int hits;
	private volatile int selfHit;

	private volatile long totalTime;
	private volatile long jpaTime;
	private volatile long jdbcTime;
	private volatile long dbTime;

	/**
	 * @param key
	 *            the key
	 * 
	 * @since 2.0.0
	 */
	public TimeElement(String key) {
		this.key = key;
	}

	/**
	 * @param worked
	 *            the time used
	 * @param self
	 *            time used by self
	 * @param timeType
	 *            time is in derby stack
	 * 
	 * @since 2.0.0
	 */
	public void addTime(long worked, boolean self, TimeType timeType) {
		this.hits++;
		this.totalTime += worked;

		switch (timeType) {
			case JPA:
				this.jpaTime += worked;
				break;
			case JDBC:
				this.jdbcTime += worked;
				break;
			default:
				this.dbTime += worked;
		}

		if (self) {
			this.selfHit++;
			this.self += worked;
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 */
	@Override
	public int compareTo(TimeElement o) {
		return this.key.compareTo(o.key);
	}

	/**
	 * Dumps the summary of the test.
	 * 
	 * @param type
	 *            the benchmark type
	 * @param totalTime
	 *            the total time
	 * @param jdbcTotalTime
	 *            the total JDBC Time
	 * @param dbTotalTime
	 *            the total DB time
	 * @param jpaTotalTime
	 *            the toal JPA time
	 * 
	 * @since 2.0.0
	 */
	public void dump0(Type type, MutableLong totalTime, MutableLong dbTotalTime, MutableLong jdbcTotalTime, MutableLong jpaTotalTime) {
		final int nameStart = this.key.indexOf("doBenchmark");

		if (nameStart > -1) {
			final long time = this.totalTime / 1000000;
			final long dbTime = this.dbTime / 1000000;
			final long jdbcTime = this.jdbcTime / 1000000;
			final long jpaTime = this.jpaTime / 1000000;

			totalTime.add(time);
			dbTotalTime.add(dbTime);
			jdbcTotalTime.add(jdbcTime);
			jpaTotalTime.add(jpaTime);

			System.err.println(//
			this.key.substring(nameStart + 11) + " Test" //
				+ "\t" + String.format("%08d", time) //
				+ "\t" + String.format("%08d", jpaTime) //
				+ "\t" + String.format("%08d", jdbcTime) //
				+ "\t" + String.format("%08d", dbTime));
		}

		final List<TimeElement> children = Lists.newArrayList(this.values());
		Collections.sort(children);
		for (final TimeElement child : children) {
			child.dump0(type, totalTime, dbTotalTime, jdbcTotalTime, jpaTotalTime);
		}
	}

	/**
	 * @param rowNo
	 *            the row no
	 * @param depth
	 *            the depth
	 * @return the row no
	 * 
	 * @since 2.0.0
	 */
	public int dump1(int rowNo, int depth) {
		if (depth > 0) {
			rowNo++;
			final String tabs = StringUtils.repeat(" ", depth);
			System.err.println(String.format("%010d", rowNo) + //
				" " + String.format("%010d", depth) + //
				" " + String.format("%010d", this.hits) + //
				" " + String.format("%010d", this.selfHit) + //
				" " + String.format("%010d", this.totalTime) + //
				" " + String.format("%010d", this.jpaTime) + //
				" " + String.format("%010d", this.jdbcTime) + //
				" " + String.format("%010d", this.dbTime) + //
				" " + String.format("%010d", this.self) + //
				tabs + this.key);
		}

		final List<TimeElement> children = Lists.newArrayList(this.values());
		Collections.sort(children);
		for (final TimeElement child : children) {
			rowNo = child.dump1(rowNo, depth + 1);
		}

		return rowNo;
	}

	/**
	 * @param rowNo
	 *            the row no
	 * 
	 * @since 2.0.0
	 */
	public void dump2(int rowNo) {
		System.err.println(String.format("%010d", rowNo) + //
			" " + String.format("%010d", this.hits) + //
			" " + String.format("%010d", this.selfHit) + //
			" " + this.key);
	}

	/**
	 * {@inheritDoc}
	 * 
	 */
	@Override
	public TimeElement get(Object key) {
		TimeElement timeElement = super.get(key);

		if (timeElement == null) {
			this.put((String) key, timeElement = new TimeElement((String) key));
		}

		return timeElement;
	}

	/**
	 * @return self
	 * 
	 * @since 2.0.0
	 */
	public Long getSelf() {
		return this.self;
	}
}
