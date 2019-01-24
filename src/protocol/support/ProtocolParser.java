package protocol.support;

import java.io.DataInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import domain.Member;
import domain.Movie;
import protocol.Packet;
import protocol.enums.Body;
import protocol.enums.Header;
import protocol.enums.Mode;

public class ProtocolParser {
	
	private static final short VERSION = 1;
	
	public Packet getObjectPacket(DataInputStream dataInputStream) throws Exception {
		Packet packet = new Packet();
		packet.setMode(dataInputStream.readInt());
		packet.setContentLength(dataInputStream.readInt());

		if(packet.getContentLength() > 1024) {
			System.out.println("Content-Length�� ���� ��ȿ���� �ʽ��ϴ�. : " + "[Packet�� ����: " + packet.getContentLength() + "]");
			return null;
		}
		
		packet.setVersion(dataInputStream.readShort());
		
		if(packet.getVersion() != VERSION) {
			System.out.println("Protocol�� Version�� ��ġ���� �ʽ��ϴ�. : " + "[���� �������� ����: " + VERSION + ", Ŭ���̾�Ʈ �������� ����: " + packet.getVersion() + "]");
			return null;
		}

		byte[] byteData = new byte[packet.getContentLength()];
		dataInputStream.readFully(byteData, 0, packet.getContentLength());
		packet.setData(new String(byteData).trim());
		System.out.println("1111111111111");
		System.out.println(packet.toString());
		
/*		String[] data = String.valueOf(packet.getData()).split(",");
		
		if(packet.getMode() == Mode.SIGNIN.getCode()) {			// �α���
			
			Member member = new Member();
			member.setMemberName(data[0].trim());
			member.setPassword(data[1].trim());
			packet.setData(member);
			
		} else if(packet.getMode() == Mode.SIGNUP.getCode()) {	// ȸ������
			
			Member member = new Member();
			member.setMemberName(data[0].trim());
			member.setPassword(data[1].trim());
			member.setName(data[2].trim());
			packet.setData(member);
			
		} else if(packet.getMode() == Mode.BOOK.getCode()) {	// �����ϱ�
			//
		} else if(packet.getMode() == Mode.MOVIE.getCode()) {	// ��ȭ����
			
			packet.setData(data[0].trim());
			
		} else if(packet.getMode() == Mode.SCHEDULE.getCode()) { // ����
			
			Map<String, String> dataMap = new HashMap<>();
			dataMap.put("scheduleId", data[0].trim());
			packet.setData(dataMap);
			
		} else if(packet.getMode() == Mode.SEAT.getCode()) { // ����
			
			Map<String, String> dataMap = new HashMap<>();
			dataMap.put("people", data[0].trim());
			dataMap.put("seat", data[1].trim());
			packet.setData(dataMap);
			
			Integer seat = Integer.parseInt(data[0].trim());
			int people = Integer.parseInt(data[1].trim());
			
			Integer[] seats = new Integer[people];
			for(int i=0; i<people; i++) {
				seats[i] = seat++;
			}
			packet.setData(seats);
			
		} else if(packet.getMode() == Mode.PAY.getCode()) {	// ����
			
			System.out.println("ProtocolParser PAY.............................");
			
			packet.setData(new BigInteger(String.valueOf(data[0].trim())));
			
		} else if(packet.getMode() == Mode.CANCEL.getCode()) {	// ��������
			
			packet.setData(data[0].trim());
			
		} else if(packet.getMode() == Mode.CHECK.getCode()) {	// ���� ��ȸ
			//
		} else if(packet.getMode() == Mode.BROADCAST.getCode()) {	// ��������
			
			if(data.length > 0) {
				packet.setData(data[0]);
			} else {
				packet.setData("");
			}
			
		}*/
		
		
		System.out.println("1. Ŭ���̾�Ʈ���� ���� ��Ŷ =======");
		System.out.println(packet.toString());
		System.out.println("===================================\n");
		
		return packet;
	}
	
	@SuppressWarnings("unchecked")
	public byte[] getBytePacket(int mode, Object data) throws UnsupportedEncodingException {

		Packet packet = new Packet();
		packet.setMode(mode);
		packet.setVersion(VERSION);
		
		if(data != null && !String.valueOf(data).isEmpty() && data != Body.INVALID.getCode()) {

			if(packet.getMode() == Mode.SIGNIN.getCode()) {		// �α���

				packet.setData(new StringJoiner(",").add(String.valueOf(((Member)data).getMemberId()))
						.add(((Member)data).getMemberName())
						.add(((Member)data).getPassword())
						.add(((Member)data).getName())
						.add(((Member)data).getRole())
						.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(((Member)data).getCreatedDate()))
						.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(((Member)data).getUpdatedDate()))
						.toString());
				
			} else if(packet.getMode() == Mode.SIGNUP.getCode()) {	// ȸ������
				
				packet.setData(data);
				
			} else if(packet.getMode() == Mode.BOOK.getCode()) {	// �����ϱ�
				
				StringJoiner hashStringJoiner = new StringJoiner("#");
				
				List<Movie> movies = (ArrayList<Movie>) data;
				for(Movie movie : movies) {
					StringJoiner commaStringJoiner = new StringJoiner(",");
					hashStringJoiner.add(commaStringJoiner.add(String.valueOf(movie.getMovieId()))
							.add(movie.getTitle())
							.add(movie.getDirector())
							.add(movie.getActor())
							.toString());
				}
				packet.setData(hashStringJoiner.toString());
				
			} else if(packet.getMode() == Mode.MOVIE.getCode()) {	// ��ȭ����
				
				StringJoiner hashStringJoiner = new StringJoiner("#");
				
				List<Map<String,String>> resultList = (ArrayList<Map<String, String>>) data;
				
				for(Map<String, String> resultMap : resultList) {
					StringJoiner commaStringJoiner = new StringJoiner(",");
					hashStringJoiner.add(commaStringJoiner.add(resultMap.get("scheduleId"))
							.add(resultMap.get("scheduleDate"))
							.add(resultMap.get("theaterId"))
							.toString());
				}
				packet.setData(hashStringJoiner.toString());
				
			} else if(packet.getMode() == Mode.SCHEDULE.getCode()) {	// ����
				
				StringJoiner hashStringJoiner = new StringJoiner("#");
				
				List<Map<String,String>> resultList = (ArrayList<Map<String, String>>) data;
				
				for(Map<String, String> resultMap : resultList) {
					StringJoiner commaStringJoiner = new StringJoiner(",");
					hashStringJoiner.add(commaStringJoiner.add(resultMap.get("seatId"))
							.add(resultMap.get("available")).toString());
				}
				packet.setData(hashStringJoiner.toString());
				
			} else if(packet.getMode() == Mode.SEAT.getCode()) {	// �¼�����
				
				packet.setData(data);
				
			} else if(packet.getMode() == Mode.PAY.getCode()) {	// ����
				
				packet.setData(data);
				
			}else if(packet.getMode() == Mode.CANCEL.getCode()) {	// ����
				
				packet.setData(data);
				
			} else if(packet.getMode() == Mode.CHECK.getCode()) {	// ��ȭ����
				
				StringJoiner hashStringJoiner = new StringJoiner("#");
				
				List<Map<String,String>> resultList = (ArrayList<Map<String, String>>) data;
				
				for(Map<String, String> resultMap : resultList) {
					StringJoiner commaStringJoiner = new StringJoiner(",");
					hashStringJoiner.add(commaStringJoiner.add(resultMap.get("orderId"))
							.add(resultMap.get("totalPrice"))
							.add(resultMap.get("orderDate"))
							.add(resultMap.get("title"))
							.add(resultMap.get("scheduleDate"))
							.toString());
				}
				packet.setData(hashStringJoiner.toString());
				
			} else if(packet.getMode() == Mode.BROADCAST.getCode()) {	// ��������
				
				packet.setData(data);
				
			} else if(packet.getMode() == Mode.SIGNOUT.getCode()) {		// �α׾ƿ�
				
				packet.setData(data);
				
			} else if(packet.getMode() == Mode.DELETE_ACCOUNT.getCode()) {		// ȸ�� Ż��
				
				packet.setData(data);
				
			}
			
		} else {
			packet.setData(data);
		}
		
		byte[] byteData = String.valueOf(packet.getData()).getBytes();
		int contentLength = byteData.length;
		packet.setContentLength(String.valueOf(packet.getData()).getBytes("EUC_KR").length);

		ByteBuffer headerByteBuffer = ByteBuffer.allocate(Header.HEADER.getLength());
		headerByteBuffer.putInt(packet.getMode())
						.putInt(packet.getContentLength())
						.putShort(packet.getVersion());
		
		System.out.println("2. �������� ������ ��Ŷ ==============");
		System.out.println(packet.toString());
		System.out.println("======================================\n");
		
		ByteBuffer packetByteBuffer = ByteBuffer.allocate(Header.HEADER.getLength() + contentLength);
		
		packetByteBuffer.put(headerByteBuffer.array()) //header
						.put(byteData); //body
		
		return packetByteBuffer.array();
	}
}
