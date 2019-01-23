package domain;

public class MemberRating {

	private int memberId;
	private int movieId;
	private float rating;
	
	// Getter, Setter method ����
	public int getMemberId() {return memberId;}
	public void setMemberId(int memberId) {this.memberId = memberId;}
	public int getMovieId() {return movieId;}
	public void setMovieId(int movieId) {this.movieId = movieId;}
	public float getRating() {return rating;}
	public void setRating(float rating) {this.rating = rating;}
	
	// ToString �������̵�
	@Override
	public String toString() {
		return "MemberRating [memberId=" + memberId + ", movieId=" + movieId + ", rating=" + rating + "]";
	}
}
