package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import config.DBCPConfig;
import domain.Member;
import domain.Movie;
import domain.Schedule;
import domain.enums.Rank;
import domain.enums.Role;
import protocol.Packet;
import protocol.enums.Body;
import protocol.enums.Mode;
import protocol.support.BodyValidator;
import protocol.support.ProtocolParser;
import server.persistence.BookRepository;
import server.persistence.MemberRepository;

public class Server {
	
	private ExecutorService executorService;
	private ServerSocket serverSocket;
//	private List<ClientHandler> connections = Collections.synchronizedList(new ArrayList<>());
	private List<ClientHandler> connections = new ArrayList<>();
	private final ReadWriteLock connectionReadWriteLock = new ReentrantReadWriteLock();
	
	
	public static void main(String[] args) {
		new Server().startService();
	}

	// ���� �޼ҵ�
	private void startService() {
		System.out.println("������Ǯ�� �����մϴ�.");
		executorService = new ThreadPoolExecutor(
				100,
				200,
				150L,
				TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>()
		);
		
		DBCPConfig dbcpConfig = DBCPConfig.getDataSource();
		Connection connection = dbcpConfig.getConnection();
		DBCPConfig.getDataSource().freeConnection(connection);

		try {
			serverSocket = new ServerSocket();		
			serverSocket.bind(new InetSocketAddress("localhost", 18080));
		} catch(Exception e) {
			if(!serverSocket.isClosed()) { stopService(); }
			return;
		}
		
		Runnable runnable = new Runnable() {
			@Override
			public void run() {	
				System.out.println("������ ���۵Ǿ����ϴ�.");
				while(true) {
					try {
						Socket socket = serverSocket.accept();
						System.out.println("[���� ����: " + socket.getRemoteSocketAddress()  + ": " + Thread.currentThread().getName() + "]");		
						
						connectionReadWriteLock.writeLock().lock();
						ClientHandler clientHandler = new ClientHandler(socket);
						connections.add(clientHandler);
						System.out.println("[���� ����: " + connections.size() + "]");
					} catch (Exception e) {
						if(!serverSocket.isClosed()) { stopService(); }
						break;
					} finally {
						connectionReadWriteLock.writeLock().unlock();
					}
				}
			}
		};
		
		executorService.submit(runnable);
	} //startServer ��
	
	private void stopService() {
		try {
			
			/*Iterator<ClientHandler> iterator = connections.iterator();
			synchronized (connections) {
				while(iterator.hasNext()) {
					ClientHandler clientHandler = iterator.next();
					clientHandler.socket.close();
					iterator.remove();
				}
			}*/
			connectionReadWriteLock.writeLock().lock();
			Iterator<ClientHandler> iterator = connections.iterator();
			
			while(iterator.hasNext()) {
				ClientHandler clientHandler = iterator.next();
				clientHandler.socket.close();
				iterator.remove();
			}
			
			if(serverSocket!=null && !serverSocket.isClosed()) { 
				serverSocket.close(); 
			}
			
			if(executorService!=null && !executorService.isShutdown()) { 
				executorService.shutdown(); 
			}
			System.out.println("������ ����Ǿ����ϴ�.");
		} catch (Exception e) {} finally {
			connectionReadWriteLock.writeLock().unlock();
		}
		
	} // stopService ��
	// ���� �޼ҵ� ��
	
	private class ClientHandler {
		private Socket socket;
		private Member member;
		private Schedule schedule;
		private Integer[] seats;
		private Packet packet;
		BigInteger totalPrice;
		
		ClientHandler(Socket socket) {
			this.socket = socket;
			getPacket();
		}
		
		// Ŭ���̾�Ʈ ��鷯 �޼ҵ� ����
		// Ŭ���̾�Ʈ�ο� ����� ����, ����� Ŭ���̾�Ʈ�� ���� Request Packet�� ������
		private void getPacket() {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					
					try {
						while(true) {
							
							DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
							ProtocolParser protocolParser = new ProtocolParser();
							BodyValidator bodyValidator = new BodyValidator();
							
							packet = protocolParser.getObjectPacket(dataInputStream);
							
							if(bodyValidator.validate(packet)) {
								System.out.println("2. ��ȿ�� ��� ��Ŷ =======");
								System.out.println(packet.toString());
								System.out.println("===================================\n");
								
								if(packet.getMode() == Mode.SIGNIN.getCode()) { // �α���
									
									MemberRepository memberRepository = new MemberRepository();
									member = memberRepository.findByIdAndPassword(((Member)packet.getData()));
									
									if(member != null) {
										sendPacket(Mode.SIGNIN.getCode(), member);										
									} else {
										sendPacket(Mode.SIGNIN.getCode(), Body.INVALID.getCode());
									}
									
								} else if(packet.getMode() == Mode.SIGNUP.getCode()) { // ȸ������
									
									MemberRepository memberRepository = new MemberRepository();
									int isSignedUp = memberRepository.save(((Member)packet.getData()));
									if(isSignedUp > 0) {
										sendPacket(Mode.SIGNUP.getCode(), isSignedUp);
									} else {
										sendPacket(Mode.SIGNUP.getCode(), Body.EMPTY.getCode());
									}
									
								} else if(packet.getMode() == Mode.BOOK.getCode()) { // �����ϱ�
									
									BookRepository bookRepository = new BookRepository();
									List<Movie> movies = bookRepository.findScheduledMovieList();
									
									sendPacket(Mode.BOOK.getCode(), movies);
									
								} else if(packet.getMode() == Mode.MOVIE.getCode()) {	// ��ȭ����

									BookRepository bookRepository = new BookRepository();
									
									List<Map<String, String>> resultList = bookRepository.findMovieSchedule(Integer.parseInt(String.valueOf(packet.getData())));
									
									sendPacket(Mode.MOVIE.getCode(), resultList);
									
								} else if(packet.getMode() == Mode.SCHEDULE.getCode()) { // ���� ����
									
									BookRepository bookRepository = new BookRepository();
									
									//int scheduleId = Integer.parseInt(((Map<String, String>)packet.getData()).get("scheduleId"));
									int scheduleId = Integer.parseInt(String.valueOf(packet.getData()));
									
									schedule = bookRepository.findSchedule(scheduleId);
									List<Map<String, String>> resultList = bookRepository.findSeats(schedule.getTheaterId(), scheduleId);
									
									sendPacket(Mode.SCHEDULE.getCode(), resultList);
									
								} else if(packet.getMode() == Mode.SEAT.getCode()) { // �¼� ����
									
									BookRepository bookRepository = new BookRepository();
									seats = (Integer[]) packet.getData();
									for(int seat : seats) System.out.print(seat + " ");
									totalPrice = bookRepository.getTicketPrice(Rank.NORMAL.getCode()).multiply(BigInteger.valueOf(seats.length));
									
									sendPacket(Mode.SEAT.getCode(), totalPrice);
									
								} else if(packet.getMode() == Mode.PAY.getCode()) { // ����
									
									BookRepository bookRepository = new BookRepository();
									
									if(totalPrice.compareTo(((BigInteger)packet.getData())) == 0) {
		
										int orderId = bookRepository.save(member.getMemberId(), schedule, totalPrice, seats);
										if(orderId > 0) { // transaction�� ����
											System.out.println(member.getMemberName() + "'s test: " + orderId);
											List<Map<String, String>> resultList = bookRepository.findOrders(orderId);
											FileBuilder fileBuilder = new FileBuilder();
											String fullPath = fileBuilder.createFile(resultList);
											byte[] byteFile = fileBuilder.convertToBytes(fullPath);
											sendPacket(Mode.PAY.getCode(), new String(byteFile));
										}
										
									} else {
										sendPacket(Mode.PAY.getCode(), Body.INVALID.getCode());
									}
									
								} else if(packet.getMode() == Mode.CANCEL.getCode()) { // ���� ���
									
									BookRepository bookRepository = new BookRepository();
									int orderId = Integer.parseInt(String.valueOf(packet.getData()));
									int memberId =bookRepository.findMembersByOrderId(orderId);
									
									if( (memberId > 0) && (memberId == member.getMemberId()) ) {
										List<Map<String, String>> resultList = bookRepository.findOrders(orderId);
										if(bookRepository.cancel(resultList)) {
											sendPacket(Mode.CANCEL.getCode(), Body.SUCCESS.getCode());
										} else {
											sendPacket(Mode.CANCEL.getCode(), Body.ERROR.getCode());
										}
									} else {
										sendPacket(Mode.CANCEL.getCode(), Body.INVALID.getCode());
									}
									
								} else if(packet.getMode() == Mode.CHECK.getCode()) { // ���� Ƽ��
									
									BookRepository bookRepository = new BookRepository();
									List<Map<String, String>> resultList = bookRepository.findCurrentOrders(member.getMemberId());
									if(resultList.size() > 0) {
										sendPacket(Mode.CHECK.getCode(), resultList);
									} else {
										sendPacket(Mode.CHECK.getCode(), Body.EMPTY.getCode());
									}
									
								} else if(packet.getMode() == Mode.BROADCAST.getCode()) { // ��������
									
									if(member.getRole().equalsIgnoreCase(Role.ADMIN.name()) || member.getRole().equalsIgnoreCase(Role.MANAGER.name())) {
										broadcast(Mode.BROADCAST.getCode(), String.valueOf(packet.getData()));
									} else {
										throw new Exception(); // Ŭ���̾�Ʈ ������ ���� Admin ������ ����Ϸ��� �Ǽ� ������̱� ������ �ٷ� ������ ����
									}
									
								} else if(packet.getMode() == Mode.SIGNOUT.getCode()) {	// �α׾ƿ�
									
									member = new Member();
									sendPacket(Mode.SIGNOUT.getCode(), Body.SUCCESS.getCode());
									
								} else if(packet.getMode() == Mode.DELETE_ACCOUNT.getCode()) {	// ȸ�� Ż��
									
									if(packet.getData() == member.getPassword()) {	// ���� �α����� ����� ��й�ȣ�� ������ ��й�ȣ�� ������ Ȯ��
										sendPacket(Mode.DELETE_ACCOUNT.getCode(), Body.SUCCESS.getCode());
									} else {
										sendPacket(Mode.DELETE_ACCOUNT.getCode(), Body.INVALID.getCode());
									}
									
								} else if (packet.getMode() == Mode.POST_MOVIE.getCode()) {
									
								} else if (packet.getMode() == Mode.POST_SCHEDULE.getCode()) {
									
								} else {
									sendPacket(packet.getMode(), Body.INVALID.getCode());	// Mode ���� �߸��Ǿ�����
								}
								
							} else {	// Body ��ȿ�� �˻� ����
								System.out.println("��ȿ���˻� ����!!!!! " + packet.getData());
								sendPacket(packet.getMode(), Body.INVALID.getCode()); // packet.getData() = "0"
							}
							
						}
						
					} catch (SocketException se) {
						
						try {
							//se.printStackTrace();
							connectionReadWriteLock.writeLock().lock();
							connections.remove(ClientHandler.this);
							System.out.println("[ó�� ������ : " + Thread.currentThread().getName() + "] Ŭ���̾�Ʈ '" + socket.getRemoteSocketAddress() + "'�� ������ ������ϴ�.");
							socket.close();
						} catch (IOException e2) {} finally {
							connectionReadWriteLock.writeLock().unlock();
						}
						
					} catch (NumberFormatException | NullPointerException | SQLException | IOException | ArrayIndexOutOfBoundsException ex) {
						
						ex.printStackTrace();
						
					} catch (Exception e) {
						try {
							e.printStackTrace();
							connectionReadWriteLock.writeLock().lock();
							connections.remove(ClientHandler.this);
							System.out.println("[ó�� ������ : " + Thread.currentThread().getName() + "] Ŭ���̾�Ʈ '" + socket.getRemoteSocketAddress() + "'�� ������ ������ϴ�.");
							socket.close();
						} catch (IOException e2) {} finally {
							connectionReadWriteLock.writeLock().unlock();
						}
					} finally {
						if(socket.isConnected()) {
							System.out.println("�������� ����!!!!!!!!!!!!!!!");
							sendPacket(packet.getMode(), Body.ERROR.getCode());
						}
					}
				}
			};
			executorService.submit(runnable);
		} // getRequest ��

		private void sendPacket(int mode, Object data) {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					try {
						DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
						ProtocolParser protocolParser = new ProtocolParser();
						
						byte[] packet = protocolParser.getBytePacket(mode, data);
						
						dataOutputStream.write(packet);
						
					} catch(Exception e) {
						try {
							//e.printStackTrace();
							connectionReadWriteLock.writeLock().lock();
							connections.remove(ClientHandler.this);
							socket.close();
						} catch (IOException e2) {} finally {
							connectionReadWriteLock.writeLock().unlock();
						}
					}
				}
			};
			executorService.submit(runnable);
		} // sendResponse ��
		
		private void broadcast(int mode, String message) {
			/*for(ClientHandler clientHandler : connections) {
				clientHandler.sendPacket(Mode.BROADCAST.getCode(), message);
				
			}*/
			connectionReadWriteLock.readLock().lock();
			try {
				for(ClientHandler clientHandler : connections) {
					clientHandler.sendPacket(Mode.BROADCAST.getCode(), message); 
				}
			} finally {
				connectionReadWriteLock.readLock().unlock();
			}
		}
		
	} // ClientHandler ��
	
}
