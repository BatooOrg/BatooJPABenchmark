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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.rmi.UnmarshalException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.JMX;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang.mutable.MutableLong;
import org.batoo.jpa.benchmark.TimeElement.TimeType;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @author hceylan
 * 
 * @since 2.0.0
 */
public class BenchmarkTest extends AbstractBenchmark {

	private static class Pipe implements Runnable {

		private static void pipe(InputStream in, OutputStream out) {
			final Thread thread = new Thread(new Pipe(in, out));
			thread.setDaemon(true);
			thread.start();
		}

		private final InputStream in;
		private final OutputStream out;

		private Pipe(InputStream in, OutputStream out) {
			this.in = in;
			this.out = out;
		}

		@Override
		public void run() {
			try {
				int i = -1;

				final byte[] buf = new byte[1024];

				while ((i = this.in.read(buf)) != -1) {
					this.out.write(buf, 0, i);
				}
			}
			catch (final Exception e) {}
		}
	}

	/**
	 * The sample rate to collect data in milliseconds
	 */
	private static final int SAMPLING_INT = Integer.valueOf(AbstractBenchmark.getProp("interval", Integer.toString(5)));;

	private static final String SEPARATOR = "_________________________________________________________________________";

	private TimeElement element;
	private final HashMap<String, TimeElement> elements = Maps.newHashMap();

	private final HashMap<Long, Long> threadCpuTimes = Maps.newHashMap();
	private long totalTime;
	private final ArrayList<Runnable> profilingQueue = Lists.newArrayList();

	private void connectStreams(final Process process) {
		Pipe.pipe(process.getInputStream(), System.out);
		Pipe.pipe(process.getErrorStream(), System.err);
	}

	private boolean isInDb(final String className) {
		return className.startsWith("org.apache.derby") //
			|| className.startsWith("com.mysql") //
			|| className.startsWith("org.h2") //
			|| className.startsWith("org.hsqldb");
	}

	private void measureTime(long worked, StackTraceElement[] stackTrace) {
		boolean gotStart = false;

		TimeElement child = this.element;
		TimeType timeType = TimeType.JPA;

		for (int i = stackTrace.length - 1; i >= 0; i--) {
			final StackTraceElement stElement = stackTrace[i];

			final String className = stElement.getClassName();

			if ((timeType == TimeType.JPA) && this.isInDb(className)) {
				timeType = TimeType.JDBC;
			}

			if (className.startsWith("java.net.So")) {
				timeType = TimeType.DB;
				break;
			}
		}

		for (int i = stackTrace.length - 1; i >= 0; i--) {
			final StackTraceElement stElement = stackTrace[i];

			if (!gotStart && !stElement.getMethodName().startsWith("singleTest")) {
				continue;
			}

			gotStart = true;
			TimeElement child2;

			final String key = AbstractBenchmark.SUMMARIZE ? //
				stElement.getClassName() + "." + stElement.getMethodName() : //
				stElement.getClassName() + "." + stElement.getMethodName() + "." + stElement.getLineNumber();

			synchronized (this) {
				child = child.get(key);
				child2 = this.elements.get(key);
				if (child2 == null) {
					this.elements.put(key, child2 = new TimeElement(key));
				}
			}

			child.addTime(worked, (i == 0), timeType);
			child2.addTime(worked, (i == 0), timeType);
		}
	}

	private void measureTimes() {
		try {
			final JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://:8989/jmxrmi");

			// launch the new process
			final BenchmarkMBean benchmarkMBean = this.tryLaunch(url);

			// wait till the warm-up finishes
			while (!benchmarkMBean.hasStarted()) {
				Thread.sleep(100);
			}

			// get the MXBean
			final JMXConnector connector = JMXConnectorFactory.connect(url);
			final ThreadMXBean mxBean = JMX.newMXBeanProxy(connector.getMBeanServerConnection(), new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME),
				ThreadMXBean.class);

			AbstractBenchmark.LOG.info("Sampling interval: {0} millisec(s)", BenchmarkTest.SAMPLING_INT);

			this.element = new TimeElement("");
			long lastTime = System.currentTimeMillis();

			// profile until the benchmark is over
			while (benchmarkMBean.isRunning()) {
				try {
					final long now = System.currentTimeMillis();
					final long diff = Math.abs(now - lastTime);
					if (diff > BenchmarkTest.SAMPLING_INT) {
						this.measureTimes(mxBean, this.profilingQueue);

						lastTime = now;
					}
					else {
						Thread.sleep(1);
					}
				}
				catch (final InterruptedException e) {}
			}

			// tell the benchmark that we are through with it and it can safely quit
			try {
				benchmarkMBean.kill();
			}
			catch (final RuntimeException e) {
				// we surely will get EOFExcetion a the benchmark will terminate and will not be able to respond.
				// so this is expected and noop.
				// otherwise rethrow it!
				if (!(e.getCause() instanceof UnmarshalException)) {
					throw e;
				}
			}
		}
		catch (final Exception e) {
			AbstractBenchmark.LOG.fatal(e, "");
		}
	}

	private void measureTimes(final ThreadMXBean mxBean, ArrayList<Runnable> profilingQueue) {
		final long[] threadIds = mxBean.getAllThreadIds();

		// initialize the start times of the threads
		for (final long threadId : threadIds) {
			// is this a benchmark thread
			final ThreadInfo threadInfo = mxBean.getThreadInfo(threadId, Integer.MAX_VALUE);

			// benchmark must have finished the work!
			if (threadInfo == null) {
				return;
			}

			if (!threadInfo.getThreadName().startsWith("BM-")) {
				continue;
			}

			final long threadCpuTime = mxBean.getThreadCpuTime(threadId);

			// Do we have the old time to create delta work
			final Long oldThreadTime = this.threadCpuTimes.get(threadId);
			if (oldThreadTime == null) {
				this.threadCpuTimes.put(threadId, threadCpuTime);
				continue;
			}

			final long worked = threadCpuTime - oldThreadTime.longValue();
			if (worked > 0) {
				this.threadCpuTimes.put(threadId, threadCpuTime);

				profilingQueue.add(new Runnable() {

					@Override
					public void run() {
						BenchmarkTest.this.measureTime(worked, threadInfo.getStackTrace());
					}
				});

			}
		}
	}

	private ThreadPoolExecutor postProcess() {
		final LinkedBlockingQueue<Runnable> processingQueue = new LinkedBlockingQueue<Runnable>(this.profilingQueue);

		final int nThreads = Runtime.getRuntime().availableProcessors();
		final ThreadPoolExecutor executor = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, processingQueue);
		executor.prestartAllCoreThreads();

		return executor;
	}

	private void prepareReport() {
		final ThreadPoolExecutor executor = this.postProcess();

		if (AbstractBenchmark.SUMMARIZE) {
			this.waitUntilFinish(executor);

			System.err.println();
			System.err.println();
			System.err.println(BenchmarkTest.SEPARATOR);
			System.err.println(MessageFormat.format(//
				"Provider {0} | DB {1} | Threads {2} | Iterations {3}" + //
					"\nTotal Run Time {4} (msec) | Samples Collected {5}", //
				AbstractBenchmark.TYPE, // 0
				AbstractBenchmark.DB, // 1
				AbstractBenchmark.THREAD_COUNT, // 2
				AbstractBenchmark.ITERATIONS, // 3
				this.totalTime, // 4
				this.profilingQueue.size() // 5
			));

			System.err.println(BenchmarkTest.SEPARATOR);
			System.err.println("Test Name\tTotal Time\tJPA Time\tJDBC Time\tDB Time");
			System.err.println(BenchmarkTest.SEPARATOR);

			final MutableLong totalTime = new MutableLong(0);
			final MutableLong dbTotalTime = new MutableLong(0);
			final MutableLong jdbcTotalTime = new MutableLong(0);
			final MutableLong jpaTotalTime = new MutableLong(0);
			this.element.dump0(AbstractBenchmark.TYPE, totalTime, dbTotalTime, jdbcTotalTime, jpaTotalTime);

			System.err.println(BenchmarkTest.SEPARATOR);
			System.err.println(//
			"TOTAL " + AbstractBenchmark.TYPE.name() + //
				" \t" + String.format("%08d", totalTime.longValue()) + //
				" \t" + String.format("%08d", jpaTotalTime.longValue()) + //
				" \t" + String.format("%08d", jdbcTotalTime.longValue()) + //
				" \t" + String.format("%08d", dbTotalTime.longValue()));
			System.err.println(BenchmarkTest.SEPARATOR);
			System.err.println();
		}
		else {
			this.element.dump1(0, 0);

			System.err.println("\n");

			int rowNo = 0;
			final ArrayList<TimeElement> elements = Lists.newArrayList(this.elements.values());
			Collections.sort(elements, new Comparator<TimeElement>() {

				@Override
				public int compare(TimeElement o1, TimeElement o2) {
					return o1.getSelf().compareTo(o2.getSelf());
				}
			});

			for (final TimeElement element : elements) {
				rowNo++;
				element.dump2(rowNo);
			}

			System.err.println("\n");
		}
	}

	/**
	 * Main test entry point
	 * 
	 * @since $version
	 */
	@Test
	public void testJpa() {
		this.measureTimes();

		final long started = System.currentTimeMillis();
		// this.waitUntilFinish(pool);
		this.totalTime = (System.currentTimeMillis() - started);

		try {
			Thread.sleep(1000);
		}
		catch (final InterruptedException e) {}

		this.prepareReport();
	}

	private BenchmarkMBean tryLaunch(JMXServiceURL url) throws IOException {
		try {
			final JMXConnector connector = JMXConnectorFactory.connect(url);

			// destroy and existing running benchmark implementation
			final BenchmarkMBean mxBenchmarkBean = JMX.newMXBeanProxy(connector.getMBeanServerConnection(), //
				new ObjectName(BenchmarkMBean.OBJECT_NAME), BenchmarkMBean.class);

			mxBenchmarkBean.kill();
		}
		catch (final Exception e) {}

		// find the 'java' executable
		final String javaCmd = System.getProperty("java.home") + //
			(System.getProperty("os.name").toLowerCase().indexOf("win") >= 0 ? "/bin/java.exe" : "/bin/java");

		// generate the command
		final String command = javaCmd + " -Xms2048m" + //
			" -DmaxTime=" + AbstractBenchmark.MAX_TEST_TIME + //
			" -Dprovider=" + AbstractBenchmark.TYPE.name() + //
			" -Diterations=" + AbstractBenchmark.ITERATIONS + //
			" -Dsummary=" + AbstractBenchmark.SUMMARIZE + //
			" -Dthreads=" + AbstractBenchmark.THREAD_COUNT + //
			" -Ddb=" + AbstractBenchmark.DB + //
			" -Dcom.sun.management.jmxremote" + //
			" -Dcom.sun.management.jmxremote.port=8989" + //
			" -Dcom.sun.management.jmxremote.authenticate=false" + //
			" -Dcom.sun.management.jmxremote.ssl=false" + //
			" -classpath " + System.getProperty("java.class.path") + //
			" org.batoo.jpa.benchmark.Benchmark";

		// fire it up!
		final Process process = Runtime.getRuntime().exec(command);

		// give it some time to make sure it really launched
		try {
			Thread.sleep(500);
		}
		catch (final InterruptedException e1) {}

		try {
			this.connectStreams(process);

			final JMXConnector connector = JMXConnectorFactory.connect(url);

			return JMX.newMXBeanProxy(connector.getMBeanServerConnection(), //
				new ObjectName(BenchmarkMBean.OBJECT_NAME), BenchmarkMBean.class);
		}
		catch (final Exception e) {
			return null;
		}
	}
}
