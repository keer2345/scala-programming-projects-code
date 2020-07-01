package retcalc

object RetCalc {
  def futureCapital(
      interestRate: Double,
      nbOfMonths: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Double = {
    val monthlySavings = netIncome - currentExpenses

    (0 until nbOfMonths).foldLeft(initialCapital)((accumulated, _) =>
      accumulated * (1 + interestRate) + monthlySavings
    )

    // def foldLeft[B](z: B)(op: (B, A) => B): B
    //
    // 0: initialCapital
    // 1: initialCapital * (1 + interestRate) + monthlySaving
    // 2: [1] * (1+interestRate) + monthlySaving
    // 3: [2] * (1+interestRate) + monthlySaving
    // ...
  }
}
