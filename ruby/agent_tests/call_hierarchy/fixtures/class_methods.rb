class Processor
  def self.parse(input)
    input.strip
  end

  def self.handle(data)
    parse(data)
  end

  def self.process(items)
    items.map { |i| parse(i) }
  end
end