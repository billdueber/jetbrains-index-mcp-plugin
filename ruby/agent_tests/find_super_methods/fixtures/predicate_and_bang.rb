class Base
  def save!
    true
  end

  def admin?
    false
  end
end

class Sub < Base
  def save!
    false
  end

  def admin?
    true
  end
end