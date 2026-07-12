# Simple module with no methods
module EmptyModule; end

# Module with methods
module Greetable
  def greet
    "hello"
  end

  def farewell(name)
    "bye #{name}"
  end
end

# Class with inheritance
class Animal
  def speak
    "sound"
  end
end

class Dog < Animal
  def bark
    "woof"
  end
end

# Class with includes, extends, prepends
class User
  include Greetable
  extend Commentable
end

# Class with class methods
class Calculator
  def self.square(x)
    x * x
  end

  def add(x, y)
    x + y
  end

  def compute
    add(1, 2)
  end
end

# Multiple top-level classes
class FirstClass
  def method_one; end
end

class SecondClass
  def method_two; end
end

# Nested modules
module Outer
  module Inner
    class Nested
      def inside
        true
      end
    end
  end
end