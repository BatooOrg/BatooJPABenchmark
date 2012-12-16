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
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.lang.mutable.MutableByte;
import org.batoo.common.log.BLogger;
import org.batoo.common.log.BLoggerFactory;

/**
 * Socket performance test mock-up.
 * 
 * @author hceylan
 * @since $version
 */
public class SocketTest {

	private static final BLogger LOG = BLoggerFactory.getLogger(SocketTest.class);

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
		new SocketTest().startServer();

		for (int i = 0; i < SocketTest.NO_SOCKS; i++) {
			new SocketTest().startClient();
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

	private void simulateComm(final Socket socket) throws IOException {
		final OutputStream os = socket.getOutputStream();
		final InputStream is = socket.getInputStream();

		long writeSize = 0;

		while (true) {
			for (final byte[] element : this.sample) {
				os.write(element);
				os.write('\n');

				os.flush();

				writeSize += element.length;
				if (writeSize > 5000000) {
					SocketTest.LOG.info("1 MB mark");
					writeSize = 0;
				}

				while (is.read() != '\n') {} // simulate read
			}
		}
	}

	private void startClient() throws Exception {
		new Thread() {
			@Override
			public void run() {
				SocketTest.this.generateData(300);

				try {
					SocketTest.this.simulateComm(new Socket("localhost", SocketTest.PORT));
				}
				catch (final Exception e) {}
			}
		}.start();
	}

	private void startServer() throws Exception {
		this.generateData(5000);

		final ServerSocket server = new ServerSocket(SocketTest.PORT, 16);
		final MutableByte clientNo = new MutableByte(0);

		// start server sock and Acceptor thread
		new Thread("Acceptor") {
			private void accept() {
				try {
					final Socket clientConn = server.accept();
					clientNo.increment();

					new Thread("ClientThread-" + clientNo.byteValue()) {
						@Override
						public void run() {
							try {
								SocketTest.this.simulateComm(clientConn);
							}
							catch (final IOException e) {
								SocketTest.LOG.error(e, "");
							}
						};
					}.start();
				}
				catch (final Exception e) {
					SocketTest.LOG.error(e, "");
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
