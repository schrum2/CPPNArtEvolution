package edu.southwestern.evolution.mutation.tweann;

import edu.southwestern.evolution.mutation.Mutation;
import edu.southwestern.networks.TWEANN;
import edu.southwestern.parameters.Parameters;
import edu.southwestern.util.random.RandomNumbers;

/**
 * General template for all mutations affecting a TWEANN.
 *
 * @author Jacob Schrum
 */
public abstract class TWEANNMutation extends Mutation<TWEANN> {

	// Every mutation has its own rate of occurrence
	protected double rate;

	/**
	 * Constructor retrieves the appropriate rate from the parameters.
	 *
	 * @param rateName
	 *            Parameter label for this mutation rate.
	 */
	public TWEANNMutation(String rateName) {
		this(Parameters.parameters.doubleParameter(rateName));
	}

	/**
	 * Constructor is provided with actual mutation rate.
	 * 
	 * @param rate
	 *            Rate of mutation: between 0 and 1
	 */
	public TWEANNMutation(double rate) {
		assert 0 <= rate && rate <= 1 : "Mutation rate out of range: " + rate;
		this.rate = rate;
	}

	/**
	 * Only perform the mutation if a random double is less than the mutation
	 * rate.
	 * 
	 * @return Whether to perform mutation
	 */
	@Override
	public boolean perform() {
		return (RandomNumbers.randomGenerator.nextDouble() < rate);
	}
}
