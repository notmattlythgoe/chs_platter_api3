package iif.th.chsplatter.chsplatter_apiv3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;

import net.thefletcher.tbaapi.v3client.ApiClient;
import net.thefletcher.tbaapi.v3client.ApiException;
import net.thefletcher.tbaapi.v3client.Configuration;
import net.thefletcher.tbaapi.v3client.api.DistrictApi;
import net.thefletcher.tbaapi.v3client.api.EventApi;
import net.thefletcher.tbaapi.v3client.api.TeamApi;
import net.thefletcher.tbaapi.v3client.auth.ApiKeyAuth;
import net.thefletcher.tbaapi.v3client.model.Award;
import net.thefletcher.tbaapi.v3client.model.AwardRecipient;
import net.thefletcher.tbaapi.v3client.model.EliminationAlliance;
import net.thefletcher.tbaapi.v3client.model.Event;
import net.thefletcher.tbaapi.v3client.model.EventRanking;
import net.thefletcher.tbaapi.v3client.model.EventRankingRankings;
import net.thefletcher.tbaapi.v3client.model.EventSimple;
import net.thefletcher.tbaapi.v3client.model.Team;

public class App {
	
	public static void main(String... args) throws Exception {
		ApiClient defaultClient = Configuration.getDefaultApiClient();
		

		// Configure API key authorization: apiKey
		ApiKeyAuth apiKey = (ApiKeyAuth) defaultClient.getAuthentication("apiKey");
		apiKey.setApiKey("epPoAKTstHeNGcwOxIQX8AuN4wfzkodaHIqmChzbqgKPG0tY1xzTOSTMH8FOpfoc");
		// Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
		//apiKey.setApiKeyPrefix("Token");

		runChsPlatter();
		// calculateAvergeAwardRank();
	}

	private static void calculateAvergeAwardRank() throws Exception{
		EventApi eventApi = new EventApi();
		TeamApi teamApi = new TeamApi();

		List<EventSimple> events = eventApi.getEventsByYearSimple(new BigDecimal(2019), null);
		double sum = 0;
		int total = 0;
		for(EventSimple event : events) {
			if (event.getEventType() != 0) {
				continue;
			}
			for (Award award : eventApi.getEventAwards(event.getKey(), null)) {
				if (
					award.getAwardType() == 0 
					// || award.getAwardType() == 1 
					// || award.getAwardType() == 9 
					// || award.getAwardType() == 10
					) {
						for (AwardRecipient recipient : award.getRecipientList()) {
							EventRanking ranking = eventApi.getEventRankings(event.getKey(), null);
							for (EventRankingRankings rank : ranking.getRankings()) {
								if (rank.getTeamKey().equals(recipient.getTeamKey())) {
									double teamRank = rank.getRank();
									sum += (teamRank / ranking.getRankings().size());
									total++;
									break;
								}
							}
						}
					}
			}
		}
		System.out.println(sum / total);
	}

	private static void runChsPlatter() {

		EventApi eventApi = new EventApi();
		DistrictApi districtApi = new DistrictApi();
		TeamApi teamApi = new TeamApi();
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		int yearsToAverage = 3;
		int type = 1;
		try {
			int year = Calendar.getInstance().get(Calendar.YEAR);
			System.out.println("Event(1), District(2), Team List(3): ");
			type = Integer.valueOf(br.readLine());

			System.out.print("Code: ");
			List<Team> teams;
			if (type == 1) {
				teams = eventApi.getEventTeams(year + br.readLine(), null);
			} else if (type == 2) {
				teams = districtApi.getDistrictTeams(year + br.readLine(), null);
			} else {
				teams = importTeamList(br, teamApi);
			}

			System.out.print("Years: ");
			yearsToAverage = Integer.valueOf(br.readLine());
			
			System.out.println("Delimiter: ");
			String delimiter = br.readLine();
			Map<String, List<EliminationAlliance>> fetchedEvents = new HashMap<String, List<EliminationAlliance>>();
			try {
				for (Team team : teams) {
					double numEvents = 0;
					double numPoints = 0;
					for (int i = 0; i < yearsToAverage; i++) {
						List<Event> events = teamApi.getTeamEventsByYear("frc" + team.getTeamNumber(), new BigDecimal(year - i), null);
						for (Event event : events) {
							if (event.getEventType() > 1 || event.getEndDate().isAfter(LocalDate.now())) {
								continue;
							}
							try {
								List<EliminationAlliance> alliances = fetchedEvents.get(event.getKey());
								if (alliances == null) {
										alliances = eventApi.getEventAlliances(event.getKey(), null);
										fetchedEvents.put(event.getKey(), alliances);
								}
								numEvents += yearsToAverage - i;
								for (int a = 0; a < 8; a++) {
									EliminationAlliance alliance = alliances.get(a);
									for (int p = 0; p < 3; p++) {
										String pick = alliance.getPicks().get(p);
										if (pick.equals(team.getKey())) {
											double eventPoints = 0;
											if (p < 2) {
												eventPoints = 16 - a;
											} else {
												eventPoints = a + 1;
											}
											if ("won".equals(alliance.getStatus().getStatus())) {
												eventPoints *= 2;
											} else if ("f".equals(alliance.getStatus().getLevel())) {
												eventPoints *= 1.66;
											} else if ("sf".equals(alliance.getStatus().getLevel())) {
												eventPoints *= 1.33;
											} 
											numPoints += eventPoints  * (yearsToAverage - i);
										}
									}
								}
							} catch (Exception e) { }
						}
					}
					System.out.println(
							new StringBuilder()
							.append(team.getTeamNumber()).append(delimiter)
							.append(team.getNickname()).append(delimiter)
							.append(team.getCity()).append(delimiter)
							.append(team.getStateProv()).append(delimiter)
							.append(numEvents == 0 ? 0 : numPoints / numEvents)
							.toString());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static List<Team> importTeamList(BufferedReader br, TeamApi teamApi) throws IOException, ApiException {
		List<Team> teams = new ArrayList<Team>();
		String team;
		do { 
			team = br.readLine();
			if (team.equals("exit")) {
				return teams;
			}
			teams.add(teamApi.getTeam("frc" + team, null));
		} while (true) ;
	}
}
