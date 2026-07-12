class Grandparent
  def talk
    "grandparent"
  end
end

class Parent < Grandparent
  def talk
    "parent"
  end
end

class Child < Parent
  def talk
    "child"
  end
end