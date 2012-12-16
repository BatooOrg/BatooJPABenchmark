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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringUtils;
import org.batoo.common.log.BLogger;
import org.batoo.common.log.BLoggerFactory;

/**
 * 
 * 
 * @author hceylan
 * @since $version
 */
public class AbstractBenchmark {

	/**
	 * The max test time, the default is 30 mins.
	 */
	protected static final long MAX_TEST_TIME = Integer.valueOf(AbstractBenchmark.getProp("maxTime", Integer.toString(30 * 60 * 1000)));

	/**
	 * The type of the test
	 */
	protected static Type TYPE = Type.valueOf(AbstractBenchmark.getProp("provider", "batoo").toUpperCase(Locale.ENGLISH));

	/**
	 * The number of tests to run
	 */
	protected static final int ITERATIONS = Integer.valueOf(AbstractBenchmark.getProp("iterations", "1000"));

	/**
	 * If the results should be summarized
	 */
	protected static final boolean SUMMARIZE = Boolean.valueOf(AbstractBenchmark.getProp("summary", Boolean.TRUE.toString()));

	/**
	 * the number of worker thread to simulate concurrency
	 */
	protected static final int THREAD_COUNT = Integer.valueOf(AbstractBenchmark.getProp("threads",
		Integer.toString(Runtime.getRuntime().availableProcessors() / 2)));

	/**
	 * the number of worker thread to simulate concurrency
	 */
	protected static final String DB = AbstractBenchmark.getProp("db", "h2");

	/**
	 * The log to use
	 */
	protected static final BLogger LOG = BLoggerFactory.getLogger("org.batoo.jpa.benchmark.Benchmark");

	/**
	 * Returns the property value.
	 * 
	 * @param key
	 *            the property key
	 * @param defaultValue
	 *            the default value
	 * @return the property values or <code>default</code>
	 * 
	 * @since $version
	 */
	protected static String getProp(String key, String defaultValue) {
		final String value = System.getProperty(key);

		return value != null ? value : defaultValue;
	}

	/**
	 * 
	 * @since $version
	 */
	public AbstractBenchmark() {
		super();
	}

	/**
	 * Creates an executor for parallel execution.
	 * 
	 * @param workQueue
	 *            the work queue
	 * @param prefix
	 *            the prefix of the thread
	 * @return the executor
	 * 
	 * @since $version
	 */
	protected ThreadPoolExecutor createExecutor(BlockingQueue<Runnable> workQueue, final String prefix) {
		final AtomicInteger nextThreadNo = new AtomicInteger(0);

		final ThreadPoolExecutor executor = new ThreadPoolExecutor(//
			AbstractBenchmark.THREAD_COUNT, AbstractBenchmark.THREAD_COUNT, // min max threads
			0L, TimeUnit.MILLISECONDS, // the keep alive time - hold it forever
			workQueue, new ThreadFactory() {

				@Override
				public Thread newThread(Runnable r) {
					final Thread t = new Thread(r);
					t.setDaemon(true);
					t.setPriority(Thread.NORM_PRIORITY);
					t.setName(prefix + "-" + nextThreadNo.incrementAndGet());

					return t;
				}
			});

		executor.prestartAllCoreThreads();

		return executor;
	}

	String etaToString(int time) {
		float timeleft = time;

		final int days = (int) ((timeleft - (timeleft % 86400)) / 86400);
		timeleft %= 86400;

		final int hours = (int) ((timeleft - (timeleft % 3600)) / 3600);
		timeleft %= 3600;

		final int mins = (int) ((timeleft - (timeleft % 60)) / 60);
		timeleft %= 60;

		final int secs = (int) timeleft;

		if (days == 0) {
			if (hours == 0) {
				return MessageFormat.format("{0,number,00} min(s) {1,number,00} sec(s)", mins, secs);
			}

			return MessageFormat.format("{0} hour(s) {1,number,00} min(s) {2,number,00} sec(s)", hours, mins, secs);
		}

		return MessageFormat.format("{0} {1,number,00} hour(s) {2,number,00} min(s) {3,number,00} sec(s)", days, hours, mins, secs);
	}

	/**
	 * Suspends the execution untile the executor finishes the work
	 * 
	 * @param executor
	 * 
	 * @since $version
	 */
	protected void waitUntilFinish(ThreadPoolExecutor executor) {
		final BlockingQueue<Runnable> workQueue = executor.getQueue();
		try {
			final long started = System.currentTimeMillis();

			int lastToGo = workQueue.size();

			final int total = workQueue.size();
			int performed = 0;

			int maxStatusMessageLength = 0;
			while (!workQueue.isEmpty()) {
				final float doneNow = lastToGo - workQueue.size();
				performed += doneNow;

				final float elapsed = (System.currentTimeMillis() - started) / 1000;

				lastToGo = workQueue.size();

				if (performed > 0) {
					final float throughput = performed / elapsed;
					final float eta = ((elapsed * total) / performed) - elapsed;

					final float percentDone = (100 * (float) lastToGo) / total;
					final int gaugeDone = (int) ((100 - percentDone) / 5);
					final String gauge = "[" + StringUtils.repeat("✓", gaugeDone) + StringUtils.repeat("-", 20 - gaugeDone) + "]";

					if ((maxStatusMessageLength != 0) || (eta > 5)) {
						String statusMessage = MessageFormat.format(
							"\r{4} %{5,number,00.00} | ETA {2} | LAST TPS {0,number,000} ops / sec | AVG TPS {1,number,000.0} | LEFT {3,number,00000}", //
							doneNow, throughput, this.etaToString((int) eta), workQueue.size(), gauge, percentDone);

						maxStatusMessageLength = Math.max(statusMessage.length(), maxStatusMessageLength);
						statusMessage = StringUtils.leftPad(statusMessage, maxStatusMessageLength - statusMessage.length());
						System.out.print(statusMessage);
					}
				}

				if (elapsed > AbstractBenchmark.MAX_TEST_TIME) {
					throw new IllegalStateException("Max allowed test time exceeded");
				}

				Thread.sleep(1000);
			}

			if (maxStatusMessageLength > 0) {
				System.out.print("\r" + StringUtils.repeat(" ", maxStatusMessageLength) + "\r");
			}

			executor.shutdown();

			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				AbstractBenchmark.LOG.warn("Forcefully shutting down the thread pool");

				executor.shutdownNow();
			}

			AbstractBenchmark.LOG.warn("Iterations completed");
		}
		catch (final InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
