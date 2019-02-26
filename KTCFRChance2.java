import java.util.Arrays;
import java.util.Random;
import java.util.TreeMap;
 
public class KTCFRChance2 {
    public long nodecount = 0; //tracking number of nodes touched
    public int brcount = 0; //tracking number of times best response function calc used
    public double[][] brex = new double[4][200]; //stores best response data
    public int itf = 0;
    public static final int PASS = 0, BET = 1, NUM_ACTIONS = 2; //game bet settings
    public static final Random random = new Random(); 
    public TreeMap<String, Node> nodeMap = new TreeMap<String, Node>();
 
    class Node {
        String infoSet; //defines infoset and terms for calculating strategy
        double[] regretSum = new double[NUM_ACTIONS], 
                 strategy = new double[NUM_ACTIONS], 
                 strategySum = new double[NUM_ACTIONS];
         
 
        private double[] getStrategy(double realizationWeight) {
            //calculates strategy as weighted and normalized regret
            double normalizingSum = 0;
            for (int a = 0; a < NUM_ACTIONS; a++) {
                strategy[a] = regretSum[a] > 0 ? regretSum[a] : 0;
                normalizingSum += strategy[a];
            }
            for (int a = 0; a < NUM_ACTIONS; a++) {
                if (normalizingSum > 0)
                    strategy[a] /= normalizingSum;
                else
                    strategy[a] = 1.0 / NUM_ACTIONS;
                strategySum[a] += realizationWeight * strategy[a];
            }
            return strategy;
        }
         
 
        public double[] getAverageStrategy() {
            //gets average strategy, which is the Nash equilibrium strategy at end
            double[] avgStrategy = new double[NUM_ACTIONS];
            double normalizingSum = 0;
            for (int a = 0; a < NUM_ACTIONS; a++)
                normalizingSum += strategySum[a];
            for (int a = 0; a < NUM_ACTIONS; a++) 
                if (normalizingSum > 0)
                    avgStrategy[a] = strategySum[a] / normalizingSum;
                else
                    avgStrategy[a] = 1.0 / NUM_ACTIONS;
            return avgStrategy;
        }
         
 
        public String toString() {
                return String.format("%4s: %s", infoSet, Arrays.toString(getAverageStrategy()));
        }
 
    }
     
 
    public void train(int iterations, int decksize, int buckets) { 
        //all variables passed from main
        //iterations defines number of cfr calls
        //decksize is the size of the deck (kuhn regular = 3)
        //buckets defines how many buckets the cards are sorted into
        //for example if decksize 100 and 4 buckets, then players are dealt cards
        //as usual, but only can play according to whether they are in 
        //bucket 1/2/3/4 (i.e. cards 1-25, 26-50, 51-75, 76-100)
        int[] cards = new int[decksize];
        long starttime = System.currentTimeMillis();
        for (int i = 0; i < decksize; i++) { 
          cards[i]=i;
        }
        double util1 = 0;
        for (int i = 0; i <= iterations; i++) { //shuffle deck and deal to p1, p2
            for (int c1 = cards.length - 1; c1 > 0; c1--) { 
                int c2 = random.nextInt(c1 + 1);
                int tmp = cards[c1];
                cards[c1] = cards[c2];
                cards[c2] = tmp;
                 
            }
            util1 += cfr(cards, "", 1, 1, decksize, starttime, i, buckets, iterations);
            //cfr called on the random deal since this is chance sampling
        }
        System.out.println("Average game value: " + util1 / iterations);
        long elapsedtime1 = System.currentTimeMillis() - starttime;
        System.out.println("Total time: " + elapsedtime1);
         
        for (int i = 0; i < brcount; i++) { //best response results
          System.out.println("Exploitability: " + brex[0][i] + " at nodecount: " + brex[1][i] + " at time: " + brex[2][i] + " at iteration: " + brex[3][i]);
        }
         
        for (Node n : nodeMap.values()) //strategy results
            System.out.println(n);
    }
     
 
    private double cfr(int[] cards, String history, double p0, double p1, int decksize, long starttime, int currit, int buckets, int iterations) {
        //passes in parameters to get current state of the game
        //history is history of all actions so far in game
        //p0 and p1 are reach probabilities of the players to this point
        // player is whose turn it is to act at the current action
        // we know player based on history.length() since play switches after each action
        int plays = history.length();
        int player = plays % 2;
        int opponent = 1 - player;
        // possible sequences in kuhn poker: 
        // pp (terminalpass), bb (doublebet), bp (terminalpass), pbp (terminalpass), pbb (doublebet)
        if (plays > 1) { //checks to see if hand is over and to return result if so
            boolean terminalPass = history.charAt(plays - 1) == 'p';
            boolean doubleBet = history.substring(plays - 2, plays).equals("bb");
            boolean isPlayerCardHigher = cards[player] > cards[opponent];
            if (terminalPass)
                if (history.equals("pp")) //double pass, higher card wins 1
                    return isPlayerCardHigher ? 1 : -1;
                else //opponent player folded to a bet, player wins 1
                    return 1;
            else if (doubleBet) //bet and call, higher card wins 2
                return isPlayerCardHigher ? 2 : -2;
        }  
         
        String infoSet = history;
         
        if (buckets > 0) {
          int bucket = 0;
          for (int i = 0; i < buckets; i++){ //figure out which bucket player is in
            if (cards[player] < (decksize/buckets)*(i+1)) {
               bucket = i;
               break;
               }
          }
          infoSet = bucket + history; //decisions made based on buckets
        }
        else {
         infoSet = cards[player] + history; //no buckets, regular infoset nodes
        }
         
        nodecount = nodecount + 1;
    if ((nodecount % 1000000) == 0)
      System.out.println("nodecount: " + nodecount);
    if ((nodecount == 32) || (nodecount == 64) || (nodecount == 128) || (nodecount == 256) || (nodecount % 10000000)==0) {
        //triggers on powers of 2 to run the best response function to check exploitability 
         double[] oppreach = new double[decksize];
         double br0 = 0;
         double br1 = 0;
          
         for (int c=0; c < decksize; c++) { //calculate default opponent reach probabilities
           for (int j = 0; j < decksize; j++) {
             if (c==j)
               oppreach[j] = 0;
             else
               oppreach[j] = 1./(oppreach.length-1);
           }
           //System.out.println("br iter: " + brf(c, "", 0, oppreach, buckets)); 
           //best response function for player 0
           br0 += brf(c, "", 0, oppreach, buckets);
         }
   
         for (int c=0; c < decksize; c++) { //calculate default opponent reach probabilities
           for (int j = 0; j < decksize; j++) {
             if (c==j)
               oppreach[j] = 0;
             else
               oppreach[j] = 1./(oppreach.length-1);
           }
           //best response function for player 1
           br1 += brf(c, "", 1, oppreach, buckets);
         }
          
         long elapsedtime = System.currentTimeMillis() - starttime;
         System.out.println("br0 " + br0);
         System.out.println("br1 " + br1);
         System.out.println("Exploitability: " + (br0+br1)/(2)); //by definition
         System.out.println("Number of nodes touched: " + nodecount);
         System.out.println("Time elapsed in milliseconds: " + elapsedtime);
         System.out.println("Iterations: " + currit);
         brex[0][brcount] = (br0+br1)/(2);
         brex[1][brcount] = nodecount;
         brex[2][brcount] = elapsedtime;
         brex[3][brcount] = currit;
         brcount = brcount + 1;
     }
         
        Node node = nodeMap.get(infoSet); //which infoset we are in to get strategy info
        if (node == null) { //if doesn't exist yet
            node = new Node();
            node.infoSet = infoSet;
            nodeMap.put(infoSet, node);
        }
 
        double[] strategy = node.getStrategy(player == 0 ? p0 : p1);
        double[] util = new double[NUM_ACTIONS];
        double nodeUtil = 0;
         
        for (int a = 0; a < NUM_ACTIONS; a++) {
            String nextHistory = history + (a == 0 ? "p" : "b");
            util[a] = player == 0 //recursively call the cfr function with new reach
                ? - cfr(cards, nextHistory, p0 * strategy[a], p1, decksize, starttime, currit, buckets, iterations)
                : - cfr(cards, nextHistory, p0, p1 * strategy[a], decksize, starttime, currit, buckets, iterations);
            nodeUtil += strategy[a] * util[a];
        }
 
        for (int a = 0; a < NUM_ACTIONS; a++) {
            double regret = util[a] - nodeUtil; //regret of using action a
            node.regretSum[a] += (player == 0 ? p1 : p0) * regret;
            //regret for action a weighted by opposing player (the counterfactual)
        }
 
        return nodeUtil;
    }
     
    private double brf(int player_card, String history, int player_iteration, double[] oppreach, int buckets)
    {  
      // same as in CFR, these evaluate how many plays and whose turn it is
      int plays = history.length();
      int player = plays % 2;
   
      // check for terminal node
      if (plays > 1) {
        double exppayoff = 0;
        boolean terminalPass = history.charAt(plays - 1) == 'p'; //check for last action being a pass
        boolean doubleBet = history.substring(plays - 2, plays).equals("bb");
        if (terminalPass || doubleBet) { //hand is terminal
          double[] oppdist = new double[oppreach.length];
          double oppdisttotal = 0;
          for (int i = 0; i < oppreach.length; i++) {
            oppdisttotal += oppreach[i]; //compute sum of distribution for normalizing later
          }
          for (int i = 0; i < oppreach.length; i++) { //entire opponent distribution
            //go through entire opponent distribution and weigh payoffs according to their card distribution
            oppdist[i] = oppreach[i]/oppdisttotal; //normalize opponent distribution
            double payoff = 0;
            boolean isPlayerCardHigher = player_card > i;
            if (terminalPass) {
              if (history.equals("pp")) {
                  payoff = isPlayerCardHigher ? 1 : -1;
              }
              else {
                if (player == player_iteration)
                  payoff = 1;
                else
                  payoff = -1;
              }
             }
            else if (doubleBet) {
                payoff = isPlayerCardHigher ? 2 : -2;
            }      
            exppayoff += oppdist[i]*payoff; //adding weighted payoffs
          }
          return exppayoff;
        }
      }
   
      double[] d = new double[NUM_ACTIONS];  //opponent action dist
      d[0] = 0;
      d[1] = 0;
   
      double[] new_oppreach = new double[oppreach.length]; //new opponent card distribution
      for (int i = 0; i < oppreach.length; i++) {
        new_oppreach[i] = oppreach[i]; 
      }
   
      double v = -100000; //initialize node utility
      double[] util = new double[NUM_ACTIONS]; //initialize util value for each action
      util[0] = 0; 
      util[1] = 0;
      double[] w = new double[NUM_ACTIONS]; //initialize weights for each action
      w[0] = 0;
      w[1] = 0;
      String infoSet = history;
      for (int a = 0; a < NUM_ACTIONS; a++) { 
        if (player != player_iteration) {
          for (int i = 0; i < oppreach.length; i++) {
            if (buckets > 0) {
              int bucket1 = 0;
              //for (int j = buckets; j => 1; j--) {
              for (int j = 0; j < buckets; j++) {
                if (i < (oppreach.length/buckets)*(j+1)) {
                  bucket1 = j;
                  break;
                }
              }
            infoSet = bucket1 + history;
            }
            else {
            infoSet = i + history; //read info set, which is hand + play history
            }
            
            
            Node node = nodeMap.get(infoSet);
            if (node == null) {
             node = new Node();
             node.infoSet = infoSet;
             nodeMap.put(infoSet, node);
             //System.out.println("infoset: " + infoSet);
             }
           
            double[] strategy = node.getAverageStrategy(); //read strategy (same as probability)
            new_oppreach[i] = oppreach[i]*strategy[a]; //update reach probability
            w[a] += new_oppreach[i]; //sum weights over all possibilities of the new reach
          }
       
        }
        String nextHistory = history + (a == 0 ? "p" : "b"); 
        util[a] = brf(player_card, nextHistory, player_iteration, new_oppreach, buckets); //recurse for each action
        if (player == player_iteration && util[a] > v) {
          v = util[a]; //this action is better than previously best action
        }
      }
   
      if (player != player_iteration) {
        // D_(-i) = Normalize(w)
        // d is action distribution that = normalized w
        d[0] = w[0]/(w[0]+w[1]);
        d[1] = w[1]/(w[0]+w[1]);
        v = d[0]*util[0] + d[1]*util[1];
      }
      return v;
   
    }
     
 
    public static void main(String[] args) {
        int iterations = 10000000;
        int decksize = 3;
        int buckets = 0; //standard options: 0, 3, 10, 25
        new KTCFRChanceFBRFIX().train(iterations, decksize, buckets);
    }
 
}
