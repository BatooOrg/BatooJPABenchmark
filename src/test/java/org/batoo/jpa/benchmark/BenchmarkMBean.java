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

/**
 * Benchmark MBean interface.
 * 
 * @author hceylan
 * @since $version
 */
public interface BenchmarkMBean {

	/**
	 * The Object name of the MBean.
	 */
	String OBJECT_NAME = "org.batoo.jpa.benchmark:type=Benchmark";

	/**
	 * Returns if the benchmark has started. The benchmark is considered as <i>started</i>after the <i>warm up</i> period is finished and
	 * the <i>real phase</i> started.
	 * 
	 * @return <code>true</code> if the benchmark is has started, <code>false</code> otherwise
	 * 
	 * @since $version
	 */
	boolean hasStarted();

	/**
	 * Returns if the benchmark is currently running.
	 * 
	 * @return <code>true</code> if the benchmark is currently running, <code>false</code> otherwise
	 * 
	 * @since $version
	 */
	boolean isRunning();

	/**
	 * Kills the benchmark. Good to kill stale benchmarks left running in the back ground.
	 * 
	 * @since $version
	 */
	void kill();
}
