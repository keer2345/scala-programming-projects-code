import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MainSpec extends AnyWordSpec with Matchers  {
  "A Person" when {
    "non-empty" should {
      "be instantiated with a age and  name" in {
          val john = Person(firstName="John",lastName="Smith",42)
          john.firstName should be("John")
          john.lastName should be("Smith")
          john.age should be(42)
      }
    }
  }
}
