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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.apache.commons.lang.mutable.MutableByte;
import org.batoo.common.log.BLogger;
import org.batoo.common.log.BLoggerFactory;

/**
 * Socket performance test mock-up.
 * 
 * @author hceylan
 * @since $version
 */
public class NioSocketTest {

	private static final BLogger LOG = BLoggerFactory.getLogger(NioSocketTest.class);

	private static final int NO_SOCKS = 1;
	private static final int PORT = 3306;

	/**
	 * @param args
	 *            the arguments
	 * @throws Exception
	 *             if an error occurs
	 * 
	 * @since $version
	 */
	public static void main(String[] args) throws Exception {
		new NioSocketTest().startServer();

		for (int i = 0; i < NioSocketTest.NO_SOCKS; i++) {
			new NioSocketTest().startClient();
		}
	}

	private final byte[][] sample = new byte[1000][];

	private void generateData(double maxPacketSize) {
		for (int i = 0; i < 1000; i++) {
			final int size = (int) ((Math.random() * maxPacketSize) + 50);
			this.sample[i] = new byte[size];

			for (int j = 0; j < size; j++) {
				this.sample[i][j] = (byte) ((Math.random() * 64) + 32);
			}
		}
	}

	private void simulateComm(final SocketChannel channel) throws IOException {
		final ByteBuffer in = ByteBuffer.allocateDirect(8192);
		final ByteBuffer out = ByteBuffer.allocateDirect(8192);

		long readSize = 0;

		while (true) {
			for (final byte[] element : this.sample) {
				out.put(element);
				out.put((byte) '\n');

				out.flip();

				while (out.hasRemaining()) {
					channel.write(out);
				}

				out.clear();

				readSize += element.length;
				if (readSize > 5000000) {
					NioSocketTest.LOG.info("5 MB mark");
					readSize = 0;
				}

				boolean done = false;
				while (!done) {
					channel.read(in);

					in.flip();

					while (in.hasRemaining()) {
						if (in.get() == '\n') {
							done = true;
							break;
						}
					};
				}

				in.clear();
			}
		}
	}

	private void startClient() throws Exception {
		new Thread() {
			@Override
			public void run() {
				NioSocketTest.this.generateData(300);

				try {
					final SocketChannel channel = SocketChannel.open();
					channel.connect(new InetSocketAddress("localhost", NioSocketTest.PORT));

					NioSocketTest.this.simulateComm(channel);
				}
				catch (final Exception e) {}
			}
		}.start();
	}

	/**
	 * 
	 * 
	 * @throws IOException
	 * @since $version
	 */
	private void startServer() throws Exception {
		this.generateData(5000);

		final ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(NioSocketTest.PORT));

		final MutableByte clientNo = new MutableByte(0);

		// start server sock and Acceptor thread
		new Thread("Acceptor") {
			private void accept() {
				try {
					final SocketChannel clientChannel = server.accept();
					clientNo.increment();

					new Thread("ClientThread-" + clientNo.byteValue()) {
						@Override
						public void run() {
							try {
								NioSocketTest.this.simulateComm(clientChannel);
							}
							catch (final IOException e) {
								NioSocketTest.LOG.error(e, "");
							}
						};
					}.start();
				}
				catch (final Exception e) {
					NioSocketTest.LOG.error(e, "");
				}
			}

			@Override
			public void run() {
				while (true) {
					this.accept();
				}
			};
		}.start();

		Thread.sleep(1000);
	}
}
