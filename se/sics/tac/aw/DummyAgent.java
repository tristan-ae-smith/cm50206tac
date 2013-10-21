/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
 *
 * Copyright (c) 2001-2005 SICS AB. All rights reserved.
 *
 * SICS grants you the right to use, modify, and redistribute this
 * software for noncommercial purposes, on the conditions that you:
 * (1) retain the original headers, including the copyright notice and
 * this text, (2) clearly document the difference between any derived
 * software and the original, and (3) acknowledge your use of this
 * software in pertaining publications and reports.  SICS provides
 * this software "as is", without any warranty of any kind.  IN NO
 * EVENT SHALL SICS BE LIABLE FOR ANY DIRECT, SPECIAL OR INDIRECT,
 * PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSSES OR DAMAGES ARISING OUT
 * OF THE USE OF THE SOFTWARE.
 *
 * -----------------------------------------------------------------
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 23 April, 2002
 * Updated : $Date: 2005/06/07 19:06:16 $
 *	     $Revision: 1.1 $
 * ---------------------------------------------------------
 * DummyAgent is a simplest possible agent for TAC. It uses
 * the TACAgent agent ware to interact with the TAC server.
 *
 * Important methods in TACAgent:
 *
 * Retrieving information about the current Game
 * ---------------------------------------------
 * int getGameID()
 *  - returns the id of current game or -1 if no game is currently plaing
 *
 * getServerTime()
 *  - returns the current server time in milliseconds
 *
 * getGameTime()
 *  - returns the time from start of game in milliseconds
 *
 * getGameTimeLeft()
 *  - returns the time left in the game in milliseconds
 *
 * getGameLength()
 *  - returns the game length in milliseconds
 *
 * int getAuctionNo()
 *  - returns the number of auctions in TAC
 *
 * int getClientPreference(int client, int type)
 *  - returns the clients preference for the specified type
 *   (types are TACAgent.{ARRIVAL, DEPARTURE, HOTEL_VALUE, E1, E2, E3}
 *
 * int getAuctionFor(int category, int type, int day)
 *  - returns the auction-id for the requested resource
 *   (categories are TACAgent.{CAT_FLIGHT, CAT_HOTEL, CAT_ENTERTAINMENT
 *    and types are TACAgent.TYPE_INFLIGHT, TACAgent.TYPE_OUTFLIGHT, etc)
 *
 * int getAuctionCategory(int auction)
 *  - returns the category for this auction (CAT_FLIGHT, CAT_HOTEL,
 *    CAT_ENTERTAINMENT)
 *
 * int getAuctionDay(int auction)
 *  - returns the day for this auction.
 *
 * int getAuctionType(int auction)
 *  - returns the type for this auction (TYPE_INFLIGHT, TYPE_OUTFLIGHT, etc).
 *
 * int getOwn(int auction)
 *  - returns the number of items that the agent own for this
 *    auction
 *
 * Submitting Bids
 * ---------------------------------------------
 * void submitBid(Bid)
 *  - submits a bid to the tac server
 *
 * void replaceBid(OldBid, Bid)
 *  - replaces the old bid (the current active bid) in the tac server
 *
 *   Bids have the following important methods:
 *    - create a bid with new Bid(AuctionID)
 *
 *   void addBidPoint(int quantity, float price)
 *    - adds a bid point in the bid
 *
 * Help methods for remembering what to buy for each auction:
 * ----------------------------------------------------------
 * int getAllocation(int auctionID)
 *   - returns the allocation set for this auction
 * void setAllocation(int auctionID, int quantity)
 *   - set the allocation for this auction
 *
 *
 * Callbacks from the TACAgent (caused via interaction with server)
 *
 * bidUpdated(Bid bid)
 *  - there are TACAgent have received an answer on a bid query/submission
 *   (new information about the bid is available)
 * bidRejected(Bid bid)
 *  - the bid has been rejected (reason is bid.getRejectReason())
 * bidError(Bid bid, int error)
 *  - the bid contained errors (error represent error status - commandStatus)
 *
 * quoteUpdated(Quote quote)
 *  - new information about the quotes on the auction (quote.getAuction())
 *    has arrived
 * quoteUpdated(int category)
 *  - new information about the quotes on all auctions for the auction
 *    category has arrived (quotes for a specific type of auctions are
 *    often requested at once).
 *
 * auctionClosed(int auction)
 *  - the auction with id "auction" has closed
 *
 * transaction(Transaction transaction)
 *  - there has been a transaction
 *
 * gameStarted()
 *  - a TAC game has started, and all information about the
 *    game is available (preferences etc).
 *
 * gameStopped()
 *  - the current game has ended
 *
 */

package se.sics.tac.aw;
import se.sics.tac.util.ArgEnumerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.Map;
import java.util.logging.*;

public class DummyAgent extends AgentImpl {

	private static final Logger log =
		Logger.getLogger(DummyAgent.class.getName());

	private static final boolean DEBUG = false;

	private static final int FLIGHTS = 8;
	private static final float FLIGHT_MIN = 150.0f;
	private static final float FLIGHT_MAX = 800.0f;

	//------------------ int z = ??;		// z per flight : bound on final peturbation. Unknown
	private static final int c = 10;		// -c : lower bound of possible z values
	private static final int d = 30;		//  d : upper bound for 
	private static final float T = 540.0f; 	//  T : total game time in seconds

	private float[] bidPrices;
	private float[] currPrices;
	private float[] flightDeltas;
	
	private List<Map<Integer, Float>> Pz;
	private ArrayList<int[]> clientEntPrefs;

	//INVESTIGATE: this code doesn't get called between games.
	//				(re-)initialisation of values moved to gameStarted()
	protected void init(ArgEnumerator args) {
		bidPrices = new float[agent.getAuctionNo()];
		currPrices = new float[agent.getAuctionNo()];
		flightDeltas = new float[FLIGHTS];
		
		Pz = new ArrayList<Map<Integer, Float>>();

	}

	public void quoteUpdated(Quote quote) {
		int auction = quote.getAuction();
		int auctionCategory = agent.getAuctionCategory(auction);
		if (auctionCategory == TACAgent.CAT_HOTEL) {
			int alloc = agent.getAllocation(auction);
			if (alloc > 0 && quote.hasHQW(agent.getBid(auction)) && quote.getHQW() < alloc) {
				Bid bid = new Bid(auction);
				// Can not own anything in hotel auctions...
				bidPrices[auction] = quote.getAskPrice() + 50;
				bid.addBidPoint(alloc, bidPrices[auction]);
				if (DEBUG) {
					log.finest("submitting bid with alloc="
							 + agent.getAllocation(auction)
							 + " own=" + agent.getOwn(auction));
				}
				agent.submitBid(bid);
			}
		} else if (auctionCategory == TACAgent.CAT_ENTERTAINMENT) {
			int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
			if (alloc != 0) {
				Bid bid = new Bid(auction);
				if (alloc < 0)
					bidPrices[auction] = 200f - (agent.getGameTime() * 120f) / 720000;
				else
					bidPrices[auction] = 50f + (agent.getGameTime() * 100f) / 720000;
				bid.addBidPoint(alloc, bidPrices[auction]);
				if (DEBUG) {
					log.finest("submitting bid with alloc="
							 + agent.getAllocation(auction)
							 + " own=" + agent.getOwn(auction));
				}
				agent.submitBid(bid);
			}
		} else if (auctionCategory == TACAgent.CAT_FLIGHT) {
			// calculate delta from last know price (ternary guard for initialisation spike)
			flightDeltas[auction] = quote.getAskPrice() - ((currPrices[auction] > 0.0f)? currPrices[auction] : quote.getAskPrice());
			currPrices[auction] = quote.getAskPrice();
			log.fine("got quote for auction " + auction + " with price " + currPrices[auction] + "( delta: " + flightDeltas[auction] + ")");
		}
	}

	public void quoteUpdated(int auctionCategory) {
		log.fine("All quotes for "
			 + agent.auctionCategoryToString(auctionCategory)
			 + " have been updated");
		if (auctionCategory == TACAgent.CAT_FLIGHT) {
			long seconds = agent.getGameTime();
			log.fine("Predicting future flight minima after " + seconds/1000 + " seconds");
			flight_predictions((int) (seconds/1000));
			expected_minimum_price((int) (seconds/1000));
			for (int i = 0; i < FLIGHTS; i++) {
				log.fine("Flight " + i + ": current price is " + currPrices[i] + ", expected minimum is " + bidPrices[i]);
				// if the game is ending soon, or current price is within 5% of the expected minimum and we need the flight
				if ((seconds > 500*1000 || currPrices[i] < 1.05 * bidPrices[i]) && agent.getAllocation(i) - agent.getOwn(i) > 0 && seconds > (20 + 10*i) * 1000) {
					log.fine("Bidding.");
					Bid bid = new Bid(i);
					bid.addBidPoint(agent.getAllocation(i) - agent.getOwn(i), currPrices[i]);
					if (DEBUG) {
						log.fine("submitting bid with alloc=" + agent.getAllocation(i)
								 + " own=" + agent.getOwn(i));
					}
					agent.submitBid(bid);
				}
			}
		}
	}

	public void bidUpdated(Bid bid) {
		log.finer("Bid Updated: id=" + bid.getID() + " auction="
			 + bid.getAuction() + " state="
			 + bid.getProcessingStateAsString());
		log.finer("       Hash: " + bid.getBidHash());
	}

	public void bidRejected(Bid bid) {
		log.warning("Bid Rejected: " + bid.getID());
		log.warning("      Reason: " + bid.getRejectReason()
		+ " (" + bid.getRejectReasonAsString() + ')');
	}

	public void bidError(Bid bid, int status) {
		log.warning("Bid Error in auction " + bid.getAuction() + ": " + status
		+ " (" + agent.commandStatusToString(status) + ')');
	}

	public void gameStarted() {
		log.fine("Game " + agent.getGameID() + " started!");
		
		//reinitialise prices, deltas and z-probabilities
		bidPrices = new float[agent.getAuctionNo()];
		currPrices = new float[agent.getAuctionNo()];
		flightDeltas = new float[FLIGHTS];
		
		Pz = new ArrayList<Map<Integer, Float>>();
		// cache the value of the uniform initial probability of any z
		// INVESTIGATE: is there an off-by-one error here? surely there are 41 z values
		float uniformP = 1.0f / (c+d);
		for (int flight = 0; flight < FLIGHTS; flight++) {
			TreeMap<Integer, Float> m = new TreeMap<Integer, Float>();
			for (int z = 0-c; z <= d; z++) {
				m.put(z, uniformP);
			}
			Pz.add(m);
		}
		log.fine("Initialised variables. Pz.size(): " + Pz.size() + " Pz[3].size(): " + Pz.get(3).size());
		log.fine("		Pz[3].get(-2): " + Pz.get(3).get(-2));

		calculateAllocation();
		sendBids();
	}

	public void gameStopped() {
		log.fine("Game Stopped!");
		for (int i = 0; i < FLIGHTS; i++) {
			log.fine("bidPrices[" + i + "]: " + bidPrices[i] + "\t currPrices[" + i + "]: " + currPrices[i]);
		}
		
	}

	public void auctionClosed(int auction) {
		log.fine("*** Auction " + auction + " closed!");
	}

	// Nested class to represent ranges for flight value peturbations 
	class Range {

			private float low, high;

			public Range(float l, float h){
					this.low = l;
					this.high = h;
			}

			public boolean contains(float number){
					return (number >= low && number <= high);
			}

			//generates a valid range given hypothetical z and t in seconds
			// (c and d are constants used in the generation of z by the server; 10 and 30 respectively)
			public Range(int t, int z) {
				float x = c + (t/T)*(z-c);
				if (x > 0) {
					this.low = 0-c;
					this.high = x;
					return;
				} else if (x < 0) {
					this.low = x;
					this.high = c;
					return;
				}
				this.low = 0-c;
				this.high = c;
			}

			// return the uniform probability of any int within the range
			public float uniformP() {
				return 1.0f / (high - low);
			}

			// return the midpoint of the range, used for expected values
			public float getMid() {
				return (high - low) / 2.0f;
			}
			
			public String toString() {
				return "(" + this.low + ") - (" + this.high + ")";
			}

	}

	// for each possible value of z for each flight, calculate the likelihood that that value
	//  is the one the server is using to generate the prices
	private void flight_predictions(int t) {
		int flightNo = 0;
		// for each flight (each initialised with possible values of z from -c to d [-10,30])
		for (Map<Integer, Float> flight : Pz) {
			
			log.fine("Calculating for flight " + flightNo + "; " + flight.size() + " values for z remain.");
			float runningTotal = 0;

			Iterator<Entry<Integer, Float>> z = flight.entrySet().iterator();
			Range r;

			// for each remaining possible value of z
			while (z.hasNext()) {
				Entry<Integer, Float> p = (Entry<Integer, Float>)z.next();
				r = new Range(t, p.getKey());		// calculate the range of possible values for y
				if ( r.contains(flightDeltas[flightNo]) ) {								// if y is within range for this z
					p.setValue( r.uniformP() * p.getValue());
					runningTotal += p.getValue();
				} else {
					log.finest("" + currPrices[flightNo] + " is outside probable range: " + flightDeltas[flightNo] + " exceeds " + r.toString());
					z.remove(); //this value of z cannot explain observed prices, discard it.
				}
			}
			
			// normalise the probablilities of each z value remaining plausible for this flight
			for (Entry<Integer, Float> p : flight.entrySet()) {
				flight.put(p.getKey(), p.getValue()/runningTotal);
			}
			
			flightNo++;
		}

		return;
	}

	// for each possible value of z for each flight, calculate the minima along an expected walk
	//  take a weighted average of these minima according to the probabilites of z
	private void expected_minimum_price(int t) {
		int flightNo = 0;
		// for each flight
		for (Map<Integer, Float> flight : Pz) {

			float runningTotal = 0.0f;
			//for each plausible value of z
			for (Map.Entry<Integer, Float> z : flight.entrySet()) {
				float min = Float.POSITIVE_INFINITY;
				float p = currPrices[flightNo]; //current price for this flight
				//simulate forwards to the end of the game
				for (int tau = t; tau <= T; tau+=10) {
					//peturbing by naive expectations of delta
					float delta = new Range(tau, z.getKey()).getMid();
					p = Math.max(FLIGHT_MIN, Math.min(FLIGHT_MAX, p + delta ));
					//track the minimum price observed
					if (p < min) {
						min = p;
					}
				}
				// multiply min by the probability that this is the one true z
				runningTotal += min * z.getValue();
			}
			// set our expected minimum for this flight to the weighted average
			bidPrices[flightNo] = runningTotal;
			flightNo++;
		}

	}

	private void sendBids() {
		for (int i = 0, n = agent.getAuctionNo(); i < n; i++) {
			int alloc = agent.getAllocation(i) - agent.getOwn(i);
			float price = -1f;
			switch (agent.getAuctionCategory(i)) {
			case TACAgent.CAT_FLIGHT:
				// don't bid on flights at the start of the game
				break;
			case TACAgent.CAT_HOTEL:
				if (alloc > 0) {
					price = 200;
					bidPrices[i] = 200f;
				}
				break;
			case TACAgent.CAT_ENTERTAINMENT:
				if (alloc < 0) {
					price = 200;
					bidPrices[i] = 200f;
				} else if (alloc > 0) {
					price = 50;
					bidPrices[i] = 50f;
				}
				break;
			default:
				break;
			}
			if (price > 0) {
				Bid bid = new Bid(i);
				bid.addBidPoint(alloc, price);
				if (DEBUG) {
					log.finest("submitting bid with alloc=" + agent.getAllocation(i)
							 + " own=" + agent.getOwn(i));
				}
				agent.submitBid(bid);
			}
		}
	}

	private void calculateAllocation() {
		clientEntPrefs = new ArrayList<int[]>();
		
		for (int i = 0; i < 8; i++) {
			int inFlight = agent.getClientPreference(i, TACAgent.ARRIVAL);
			int outFlight = agent.getClientPreference(i, TACAgent.DEPARTURE);
			int hotel = agent.getClientPreference(i, TACAgent.HOTEL_VALUE);
			int type;

			// Get the flight preferences auction and remember that we are
			// going to buy tickets for these days. (inflight=1, outflight=0)
			int auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, inFlight);
			agent.setAllocation(auction, agent.getAllocation(auction) + 1);
			auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, outFlight);
			agent.setAllocation(auction, agent.getAllocation(auction) + 1);

			// if the hotel value is greater than 70 we will select the
			// expensive hotel (type = 1)
			if (hotel > 70) {
				type = TACAgent.TYPE_GOOD_HOTEL;
			} else {
				type = TACAgent.TYPE_CHEAP_HOTEL;
			}
			// allocate a hotel night for each day that the agent stays
			for (int d = inFlight; d < outFlight; d++) {
				auction = agent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
				log.finer("Adding hotel for day: " + d + " on " + auction);
				agent.setAllocation(auction, agent.getAllocation(auction) + 1);
			}

			clientEntPrefs.add(getClientEntPrefs(i));
			bestEntDay(inFlight, outFlight, i, 0);
			
		}
		
		//loop through all of the clients, allocating them their second and third preferences if possible
		for (int pref = 1; pref <= 2; pref++) {
			for (int client = 0; client < 8; client++) {
				bestEntDay(agent.getClientPreference(client, TACAgent.ARRIVAL), agent.getClientPreference(client, TACAgent.DEPARTURE), client, pref);
			}
		}
	}

	private void bestEntDay(int inFlight, int outFlight, int client, int pref) {
		int type = clientEntPrefs.get(client)[pref];
		for (int i = inFlight; i < outFlight; i++) {
			//skip this date if the client already has allocated entertainment
			if (0-i == clientEntPrefs.get(client)[0] || 0-i == clientEntPrefs.get(client)[1]) {
				continue;
			}
			int auction = agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, type, i);
//			log.fine("getting allocation for a" + auction + " on day " + i + " with type " + type);
			if (agent.getAllocation(auction) < agent.getOwn(auction)) {
				log.finer("Adding entertainment " + type + " on " + auction);
				agent.setAllocation(auction, agent.getAllocation(auction) + 1);
				//double up on the prefs to store which days are already allocated
				clientEntPrefs.get(client)[pref] = -i;
				return;
			}
		}
		
		// If none left and needy, just take the first...
		if (pref == 0) {
			int auction = agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, type, inFlight);
			agent.setAllocation(auction, agent.getAllocation(auction) + 1);
			clientEntPrefs.get(client)[0] = -inFlight;
			return;
		}
	}

	private int[] getClientEntPrefs(int client) {
		int e1 = agent.getClientPreference(client, TACAgent.E1);
		int e2 = agent.getClientPreference(client, TACAgent.E2);
		int e3 = agent.getClientPreference(client, TACAgent.E3);
		
		int orderedPrefs[] = {0,0,0};
		
		orderedPrefs[0] = (e1 > e2 && e1 > e3)? TACAgent.TYPE_ALLIGATOR_WRESTLING : (e2 > e3)? TACAgent.TYPE_AMUSEMENT : TACAgent.TYPE_MUSEUM;
		orderedPrefs[2] = (e1 < e2 && e1 < e3)? TACAgent.TYPE_ALLIGATOR_WRESTLING : (e2 < e3)? TACAgent.TYPE_AMUSEMENT : TACAgent.TYPE_MUSEUM;
		orderedPrefs[1] = (e1 < Math.max(e1, Math.max(e2, e3)) && e1 > Math.min(e1, Math.min(e2, e3)))? TACAgent.TYPE_ALLIGATOR_WRESTLING : (e2 < Math.max(e1, Math.max(e2, e3)) && e2 > Math.min(e1, Math.min(e2, e3)))? TACAgent.TYPE_AMUSEMENT : TACAgent.TYPE_MUSEUM;
		log.fine("client " + client + ": " + orderedPrefs[0] + " " + orderedPrefs[1] + " " + orderedPrefs[2]);
		return orderedPrefs;
	}



	// -------------------------------------------------------------------
	// Only for backward compability
	// -------------------------------------------------------------------

	public static void main (String[] args) {
		TACAgent.main(args);
	}

} // DummyAgent
